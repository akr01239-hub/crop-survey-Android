package com.cropsurvey.app.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.cropsurvey.app.BaseActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cropsurvey.app.R
import com.cropsurvey.app.config.AppConfig
import com.cropsurvey.app.config.PhotoRequirement
import com.cropsurvey.app.models.CapturedPhoto
import com.cropsurvey.app.models.GpsCoords
import com.cropsurvey.app.queue.QueueManager
import com.cropsurvey.app.utils.GpsHelper
import com.cropsurvey.app.utils.SurveySession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.cropsurvey.app.network.ApiClient
import com.cropsurvey.app.utils.GeoBufferHelper

class PhotoCaptureActivity : BaseActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var tvPhotoLabel: TextView
    private lateinit var tvInstruction: TextView
    private lateinit var tvGpsCoords: TextView
    private lateinit var accuracyBar: ProgressBar
    private lateinit var btnCapture: ImageButton
    private lateinit var btnClose: ImageButton
    private lateinit var tvProgress: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var ivStampOverlay: ImageView   // live stamp preview on camera feed
    private lateinit var ivPhotoPreview: ImageView   // full photo preview after capture
    private lateinit var previewOverlay: View        // dim overlay behind confirm dialog
    private lateinit var btnConfirm: Button          // confirm = upload
    private lateinit var btnRetake: Button           // retake = discard and re-shoot

    private lateinit var surveyType: String
    private lateinit var surveyId: String
    private lateinit var caseId: String
    private lateinit var photoRequirements: List<PhotoRequirement>
    private var photoIndex = 0

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private val CAMERA_PERMISSION_CODE = 1001

    // Cell descriptor, drawStamp, renderStampBitmap, drawCell, and clip moved
    // to GeoTagOverlay (shared with VideoStampTranscoder for video stamping).

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_capture)

        surveyType        = intent.getStringExtra("survey_type") ?: "CLS"
        surveyId          = intent.getStringExtra("survey_id")   ?: ""
        caseId            = SurveySession.currentCaseId.ifEmpty { surveyId.take(8).uppercase() }
        photoIndex        = intent.getIntExtra("photo_index", 0)
        photoRequirements = AppConfig.getPhotosForType(this, surveyType)

        cameraExecutor = Executors.newSingleThreadExecutor()
        bindViews(); setupButtons(); checkPermissionsAndStartCamera(); updatePhotoUI()
    }

    private fun bindViews() {
        previewView    = findViewById(R.id.preview_view)
        tvPhotoLabel   = findViewById(R.id.tv_photo_label)
        tvInstruction  = findViewById(R.id.tv_instruction)
        tvGpsCoords    = findViewById(R.id.tv_gps_coords)
        accuracyBar    = findViewById(R.id.accuracy_bar)
        btnCapture     = findViewById(R.id.btn_capture)
        btnClose       = findViewById(R.id.btn_close)
        tvProgress     = findViewById(R.id.tv_progress)
        progressBar    = findViewById(R.id.progress_bar)
        ivStampOverlay = findViewById(R.id.iv_stamp_overlay)
        ivPhotoPreview = findViewById(R.id.iv_photo_preview)
        previewOverlay = findViewById(R.id.preview_overlay)
        btnConfirm     = findViewById(R.id.btn_confirm_photo)
        btnRetake      = findViewById(R.id.btn_retake_photo)

        // Hide preview panel initially — shown only after photo is taken
        listOf(ivPhotoPreview, previewOverlay, btnConfirm, btnRetake,
            findViewById<View>(R.id.btn_confirm_retake_bar))
            .forEach { it.visibility = View.GONE }
    }

    private fun setupButtons() {
        btnClose.setOnClickListener { finish() }
        btnCapture.setOnClickListener { capturePhoto() }
        lifecycleScope.launch {
            while (true) {
                val coords = GpsHelper.getCurrentLocation(this@PhotoCaptureActivity)
                if (coords != null) {
                    val pct = ((1 - minOf(coords.accuracy ?: 100f, 100f) / 100f) * 100).toInt()
                    accuracyBar.progress = pct

                    if (SurveySession.polygonGeoJson != null) {
                        val inside = GeoBufferHelper.isInsideBuffer(coords.lat, coords.lon)
                        runOnUiThread {
                            btnCapture.isEnabled = inside
                            tvGpsCoords.visibility = View.VISIBLE
                            if (!inside) {
                                val d = GeoBufferHelper.distanceOutsideBuffer(coords.lat, coords.lon)
                                val distText = if (d >= 1000)
                                    "${"%.2f".format(d / 1000)} km"
                                else
                                    "${d.toInt()} m"
                                tvGpsCoords.setTextColor(Color.parseColor("#DC2626"))
                                tvGpsCoords.text = "⚠ Outside field buffer by $distText — move closer to capture"
                            } else {
                                tvGpsCoords.setTextColor(Color.parseColor("#16A34A"))
                                tvGpsCoords.text = "✓ Inside field buffer — you can capture"
                            }
                        }
                    } else {
                        // No polygon set — show GPS coords so user knows location is active
                        runOnUiThread {
                            tvGpsCoords.visibility = View.VISIBLE
                            tvGpsCoords.setTextColor(Color.WHITE)
                            tvGpsCoords.text = GpsHelper.formatCoords(coords)
                        }
                    }

                    // Render live stamp overlay on camera preview
                    withContext(Dispatchers.IO) {
                        try {
                            val place = reverseGeocode(this@PhotoCaptureActivity, coords.lat, coords.lon)
                            val stampBmp = GeoTagOverlay.renderStampBitmap(1080, coords, place, SurveySession.currentSurveyId ?: "")
                            runOnUiThread { ivStampOverlay.setImageBitmap(stampBmp) }
                        } catch (_: Exception) {}
                    }
                } else {
                    // GPS not available yet
                    runOnUiThread {
                        tvGpsCoords.visibility = View.VISIBLE
                        tvGpsCoords.setTextColor(Color.parseColor("#F59E0B"))
                        tvGpsCoords.text = "⏳ Waiting for GPS signal…"
                    }
                }
                kotlinx.coroutines.delay(AppConfig.GPS_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun checkPermissionsAndStartCamera() {
        val perms = arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
        if (perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED })
            startCamera()
        else ActivityCompat.requestPermissions(this, perms, CAMERA_PERMISSION_CODE)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == CAMERA_PERMISSION_CODE && results.all { it == PackageManager.PERMISSION_GRANTED }) startCamera()
        else { Toast.makeText(this, "Camera and location permissions required", Toast.LENGTH_LONG).show(); finish() }
    }

    private fun startCamera() {
        // FILL_CENTER fills the PreviewView completely — no black bars
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        ProcessCameraProvider.getInstance(this).addListener({
            val cp = ProcessCameraProvider.getInstance(this).get()
            // Use RATIO_16_9 to match tall phone screens and avoid letterboxing
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            // Capture at the SAME 16:9 ratio as the preview, so the captured
            // photo matches what the user framed (FILL_CENTER preview was
            // cropping to 16:9 while capture defaulted to 4:3 — causing the
            // saved photo to look "zoomed out" / show more than was framed).
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setJpegQuality(AppConfig.PHOTO_QUALITY)
                .build()
            try { cp.unbindAll(); cp.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture) }
            catch (e: Exception) { Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show() }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePhoto() {
        val ic  = imageCapture ?: return
        val req = photoRequirements.getOrNull(photoIndex) ?: return
        btnCapture.isEnabled = false; progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val coords = GpsHelper.getCurrentLocation(this@PhotoCaptureActivity)
            if (coords == null) {
                Toast.makeText(this@PhotoCaptureActivity, "GPS not available. Try again.", Toast.LENGTH_SHORT).show()
                btnCapture.isEnabled = true; progressBar.visibility = View.GONE; return@launch
            }
            if (SurveySession.polygonGeoJson != null && !GeoBufferHelper.isInsideBuffer(coords.lat, coords.lon)) {
                val d = GeoBufferHelper.distanceOutsideBuffer(coords.lat, coords.lon)
                val distText = if (d >= 1000) "${"%.2f".format(d / 1000)} km" else "${d.toInt()} m"
                runOnUiThread {
                    androidx.appcompat.app.AlertDialog.Builder(this@PhotoCaptureActivity)
                        .setTitle("Outside Field Buffer")
                        .setMessage("You are $distText outside the 50 m buffer zone.\n\nPlease move closer to the field to capture photos.")
                        .setPositiveButton("OK", null).show()
                    btnCapture.isEnabled = false; progressBar.visibility = View.GONE
                }
                return@launch
            }
            val file = File(cacheDir, "${req.key}_${System.currentTimeMillis()}.jpg")  // temp raw
            ic.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(),
                ContextCompat.getMainExecutor(this@PhotoCaptureActivity),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(o: ImageCapture.OutputFileResults) {
                        lifecycleScope.launch {
                            val place = withContext(Dispatchers.IO) { reverseGeocode(this@PhotoCaptureActivity, coords.lat, coords.lon) }

                            // ── Blur check on the RAW captured frame (before watermark) ──
                            val isBlurry = withContext(Dispatchers.Default) { isImageBlurry(file) }

                            val wFile = burnWatermark(file, coords, place)

                            // ── Show preview for user to confirm before uploading ──
                            val previewBmp = BitmapFactory.decodeFile(wFile.absolutePath)
                            runOnUiThread {
                                progressBar.visibility = View.GONE
                                // Show button bar FIRST so layout is measured before image
                                previewOverlay.visibility = View.VISIBLE
                                btnConfirm.visibility     = View.VISIBLE
                                btnRetake.visibility      = View.VISIBLE
                                val bar = findViewById<View>(R.id.btn_confirm_retake_bar)
                                bar.visibility = View.VISIBLE
                                // Hide camera UI
                                previewView.visibility    = View.GONE
                                findViewById<View>(R.id.bottom_controls).visibility = View.GONE
                                findViewById<View>(R.id.iv_stamp_overlay).visibility = View.GONE
                                btnCapture.isEnabled      = false
                                // Set image after layout pass
                                bar.post {
                                    ivPhotoPreview.setImageBitmap(previewBmp)
                                    ivPhotoPreview.visibility = View.VISIBLE
                                    if (isBlurry) {
                                        androidx.appcompat.app.AlertDialog.Builder(this@PhotoCaptureActivity)
                                            .setTitle("Photo looks blurry")
                                            .setMessage("This photo appears out of focus. Hold the phone steady and try again for a clearer shot.")
                                            .setCancelable(false)
                                            .setNegativeButton("Retake") { _, _ -> btnRetake.performClick() }
                                            .setPositiveButton("Use Anyway", null)
                                            .show()
                                    }
                                }
                            }

                            btnConfirm.setOnClickListener {
                                // User happy — save + upload
                                hidePreview()
                                progressBar.visibility = View.VISIBLE
                                lifecycleScope.launch {
                                    SurveySession.addCapturedPhoto(CapturedPhoto(req.key, wFile.absolutePath, coords.lat, coords.lon, coords.accuracy))
                                    uploadPhoto(surveyId, req, wFile, coords)
                                    progressBar.visibility = View.GONE
                                    setResult(RESULT_OK); finish()
                                }
                            }

                            btnRetake.setOnClickListener {
                                // Discard — hide preview, let user shoot again
                                hidePreview()
                                btnCapture.isEnabled = true
                                wFile.delete()
                                file.delete()
                            }
                        }
                    }
                    override fun onError(e: ImageCaptureException) {
                        Toast.makeText(this@PhotoCaptureActivity, "Capture failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        btnCapture.isEnabled = true; progressBar.visibility = View.GONE
                    }
                }
            )
        }
    }

    private fun hidePreview() {
        ivPhotoPreview.visibility = View.GONE
        previewOverlay.visibility = View.GONE
        btnConfirm.visibility     = View.GONE
        btnRetake.visibility      = View.GONE
        findViewById<View>(R.id.btn_confirm_retake_bar).visibility = View.GONE
        ivPhotoPreview.setImageBitmap(null)
        // Restore camera UI
        previewView.visibility = View.VISIBLE
        findViewById<View>(R.id.bottom_controls).visibility = View.VISIBLE
        try { findViewById<View>(R.id.iv_stamp_overlay).visibility = View.VISIBLE } catch (_: Exception) {}
    }

    /** Delegates to the shared GeoTagOverlay address service (single source of truth). */
    private fun reverseGeocode(ctx: Context, lat: Double, lon: Double): String =
        GeoTagOverlay.reverseGeocode(ctx, lat, lon)

    // ─── WATERMARK ────────────────────────────────────────────────────────────
    // Reference design: compact card, bottom-right corner.
    // Header: dark navy bg, red pin circle, bold white place name.
    // Body: 4 rows × 2 cols, each cell = [pastel icon] [label] [:] [bold value]
    //       ALL on ONE horizontal line, vertically centered in the row.
    private fun burnWatermark(file: File, coords: GpsCoords, placeName: String): File {
        val rawBmp = BitmapFactory.decodeFile(file.absolutePath) ?: return file
        // Fix EXIF rotation so portrait shots aren't sideways
        val exif = android.media.ExifInterface(file.absolutePath)
        val rotation = when (exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90  -> 90f
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val original = if (rotation != 0f) {
            val matrix = Matrix(); matrix.postRotate(rotation)
            Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, matrix, true).also { rawBmp.recycle() }
        } else rawBmp
        val maxW     = AppConfig.PHOTO_MAX_WIDTH
        val sc       = maxW.toFloat() / original.width
        val bmp      = Bitmap.createScaledBitmap(original, maxW, (original.height * sc).toInt(), true)
        original.recycle()

        val out = bmp.copy(Bitmap.Config.ARGB_8888, true)
        GeoTagOverlay.drawStamp(Canvas(out), out.width.toFloat(), out.height.toFloat(), coords, placeName, surveyId)

        // Save to filesDir (NOT cacheDir) — Android never auto-deletes filesDir,
        // so the photo survives logout, cache wipes, and low-storage cleanup.
        val photoDir = File(applicationContext.filesDir, "survey_photos").also { it.mkdirs() }
        val outFile = File(photoDir, "wm_${file.name}")
        FileOutputStream(outFile).use { fos -> out.compress(Bitmap.CompressFormat.JPEG, AppConfig.PHOTO_QUALITY, fos) }
        out.recycle(); bmp.recycle()
        file.delete()  // delete temp raw file — watermarked copy in filesDir is the keeper
        return outFile
    }

    private suspend fun uploadPhoto(surveyId: String, req: PhotoRequirement, file: File, coords: GpsCoords) {
        try {
            val photoPart  = MultipartBody.Part.createFormData("photo", file.name, file.asRequestBody("image/jpeg".toMediaType()))
            val capturedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())
            val res = ApiClient.service.uploadPhoto(
                surveyId   = surveyId,
                photo      = photoPart,
                photoKey   = req.key.toRequestBody("text/plain".toMediaType()),
                label      = req.label.toRequestBody("text/plain".toMediaType()),
                lat        = coords.lat.toString().toRequestBody("text/plain".toMediaType()),
                lon        = coords.lon.toString().toRequestBody("text/plain".toMediaType()),
                accuracy   = (coords.accuracy ?: 0f).toString().toRequestBody("text/plain".toMediaType()),
                capturedAt = capturedAt.toRequestBody("text/plain".toMediaType())
            )
            if (res.isSuccessful) SurveySession.markPhotoUploaded(req.key)
            else QueueManager.enqueuePhoto(this, surveyId, req.key, req.label, file.absolutePath, coords)
        } catch (e: Exception) {
            QueueManager.enqueuePhoto(this, surveyId, req.key, req.label, file.absolutePath, coords)
        }
    }

    /**
     * Cheap blur detection: downscale to a small grayscale bitmap, run a 3x3
     * Laplacian edge filter, and measure the variance of the result. Sharp
     * images have high-variance edges; blurry images have low variance.
     * Threshold tuned conservatively to avoid false positives on legitimately
     * soft/low-texture subjects (e.g. plain field soil).
     */
    private fun isImageBlurry(file: File, threshold: Double = 35.0): Boolean {
        return try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 } // downscale ~1/4
            val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return false
            val w = bmp.width
            val h = bmp.height
            if (w < 9 || h < 9) return false

            // Grayscale pixel buffer
            val gray = IntArray(w * h)
            val pixels = IntArray(w * h)
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                gray[i] = (r * 299 + g * 587 + b * 114) / 1000
            }

            // 3x3 Laplacian kernel: [0 1 0; 1 -4 1; 0 1 0]
            var sum = 0.0
            var sumSq = 0.0
            var count = 0
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val idx = y * w + x
                    val lap = (gray[idx - w] + gray[idx + w] + gray[idx - 1] + gray[idx + 1] - 4 * gray[idx]).toDouble()
                    sum += lap
                    sumSq += lap * lap
                    count++
                }
            }
            bmp.recycle()
            if (count == 0) return false
            val mean = sum / count
            val variance = (sumSq / count) - (mean * mean)
            variance < threshold
        } catch (e: Exception) {
            false  // never block capture due to a blur-check failure
        }
    }

    private fun updatePhotoUI() {
        val req = photoRequirements.getOrNull(photoIndex) ?: return
        tvPhotoLabel.text  = req.label
        tvInstruction.text = req.instruction ?: ""
        tvProgress.text    = "${photoIndex + 1} / ${photoRequirements.size}"
        tvInstruction.visibility = if (req.instruction != null) View.VISIBLE else View.GONE
    }

    override fun onDestroy() { super.onDestroy(); cameraExecutor.shutdown() }
}