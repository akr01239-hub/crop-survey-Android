package com.cropsurvey.app.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Geocoder
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

    // ─── Cell descriptor ──────────────────────────────────────────────────────
    private data class WCell(
        val label: String,
        val value: String,
        val iconBg: Int,   // pastel circle background colour
        val iconFg: Int    // icon foreground colour
    )

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
                            val stampBmp = renderStampBitmap(coords, place, previewWidth = 1080)
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
            // Capture in highest quality — aspect ratio doesn't affect saved image quality
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
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

    private fun reverseGeocode(ctx: Context, lat: Double, lon: Double): String = try {
        val geocoder = Geocoder(ctx, Locale.getDefault())
        @Suppress("DEPRECATION")
        val addrs = geocoder.getFromLocation(lat, lon, 1)
        if (!addrs.isNullOrEmpty()) {
            val a = addrs[0]
            val place = a.subLocality?.takeIf { it.isNotBlank() } ?: a.locality?.takeIf { it.isNotBlank() } ?: a.featureName?.takeIf { it.isNotBlank() }
            val dist  = a.subAdminArea?.takeIf { it.isNotBlank() }
            val state = a.adminArea?.takeIf { it.isNotBlank() }
            listOfNotNull(place, dist, state)
                .fold(mutableListOf<String>()) { acc, s -> if (acc.none { it.equals(s, true) }) acc.add(s); acc }
                .take(3).joinToString(", ").ifEmpty { "%.5f°N, %.5f°E".format(lat, lon) }
        } else "%.5f°N, %.5f°E".format(lat, lon)
    } catch (e: Exception) { "%.5f°N, %.5f°E".format(lat, lon) }

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

        val canvas = Canvas(bmp.copy(Bitmap.Config.ARGB_8888, true).also { drawStamp(Canvas(it), it.width.toFloat(), it.height.toFloat(), coords, placeName, surveyId) })
        // Re-do properly:
        val out = bmp.copy(Bitmap.Config.ARGB_8888, true)
        drawStamp(Canvas(out), out.width.toFloat(), out.height.toFloat(), coords, placeName, surveyId)

        // Save to filesDir (NOT cacheDir) — Android never auto-deletes filesDir,
        // so the photo survives logout, cache wipes, and low-storage cleanup.
        val photoDir = File(applicationContext.filesDir, "survey_photos").also { it.mkdirs() }
        val outFile = File(photoDir, "wm_${file.name}")
        FileOutputStream(outFile).use { fos -> out.compress(Bitmap.CompressFormat.JPEG, AppConfig.PHOTO_QUALITY, fos) }
        out.recycle(); bmp.recycle()
        file.delete()  // delete temp raw file — watermarked copy in filesDir is the keeper
        return outFile
    }

    /** Renders just the stamp bitmap for the live camera overlay (no photo underneath). */
    private fun renderStampBitmap(coords: GpsCoords, placeName: String, previewWidth: Int): Bitmap {
        val W = previewWidth.toFloat()
        val unit    = W * 0.008f
        val headerH = unit * 11f
        val rowH    = unit * 9f
        val cardH   = headerH + 3 * rowH
        val bmp = Bitmap.createBitmap(previewWidth, cardH.toInt() + (unit * 3).toInt(), Bitmap.Config.ARGB_8888)
        drawStamp(Canvas(bmp), W, bmp.height.toFloat(), coords, placeName, SurveySession.currentSurveyId ?: "")
        return bmp
    }

    /** Core stamp drawing — shared by burnWatermark and renderStampBitmap. */
    private fun drawStamp(canvas: Canvas, W: Float, H: Float, coords: GpsCoords, placeName: String, survId: String) {
        val margin  = W * 0.012f
        val cardW   = W - margin * 2f
        val unit    = W * 0.008f
        val headerH = unit * 11f
        val rowH    = unit * 9f
        val cardH   = headerH + 3 * rowH
        val cardX   = margin
        val cardY   = H - cardH - margin
        val cornerR = unit * 1.8f
        val thirdW  = cardW / 3f

        // Shadow
        canvas.drawRoundRect(RectF(cardX+unit*0.6f, cardY+unit*0.6f, cardX+cardW+unit*0.6f, cardY+cardH+unit*0.6f), cornerR, cornerR,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70,0,0,0) })

        val cardRect = RectF(cardX, cardY, cardX+cardW, cardY+cardH)

        // White base (prevents black corners)
        canvas.drawRoundRect(cardRect, cornerR, cornerR,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(252,255,255,255) })

        // Clip + header + body
        val clip = Path().apply { addRoundRect(cardRect, cornerR, cornerR, Path.Direction.CW) }
        canvas.save(); canvas.clipPath(clip)
        canvas.drawRect(RectF(cardX, cardY, cardX+cardW, cardY+headerH),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.LinearGradient(cardX, cardY, cardX+cardW, cardY,
                    Color.parseColor("#02122F"), Color.parseColor("#071B46"), android.graphics.Shader.TileMode.CLAMP)
            })
        canvas.drawRect(RectF(cardX, cardY+headerH, cardX+cardW, cardY+cardH),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(252,255,255,255) })
        canvas.restore()

        // Border
        canvas.drawRoundRect(cardRect, cornerR, cornerR,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160,100,130,200); style = Paint.Style.STROKE; strokeWidth = unit*0.5f })

        // Grid dividers
        val divP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(50,180,190,210); strokeWidth = unit*0.25f }
        for (r in 1..3) canvas.drawLine(cardX, cardY+headerH+r*rowH, cardX+cardW, cardY+headerH+r*rowH, divP)
        canvas.drawLine(cardX+thirdW,   cardY+headerH, cardX+thirdW,   cardY+cardH, divP)
        canvas.drawLine(cardX+thirdW*2, cardY+headerH, cardX+thirdW*2, cardY+cardH, divP)

        // Header pin + place name
        val pinR = unit*1.8f; val pinCX = cardX+unit*2.5f+pinR; val pinCY = cardY+headerH*0.5f
        canvas.drawCircle(pinCX, pinCY, pinR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EF4444") })
        canvas.drawCircle(pinCX, pinCY, pinR*0.36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        val hPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = unit*3.0f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val placeX = pinCX+pinR+unit*1.2f
        canvas.drawText(clip(placeName, hPaint, cardX+cardW-placeX-unit*1.5f), placeX, pinCY+unit*0.9f, hPaint)

        // Data
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val tdf = SimpleDateFormat("hh:mm:ss aa", Locale.getDefault())
        val now = Date()
        val uId = SurveySession.employeeId.ifEmpty { SurveySession.userId.take(8).uppercase().ifEmpty { "—" } }
        val sId = if (survId.isNotEmpty()) caseId.ifEmpty { survId.take(8).uppercase() } else "—"
        val alt = coords.altitude?.let { "${"%.1f".format(it)}m" } ?: "0m"
        val acc = "±${coords.accuracy?.toInt() ?: 0}m"

        val blue   = Pair(Color.parseColor("#DBEAFE"), Color.parseColor("#1D4ED8"))
        val green  = Pair(Color.parseColor("#D1FAE5"), Color.parseColor("#047857"))
        val purple = Pair(Color.parseColor("#EDE9FE"), Color.parseColor("#6D28D9"))
        val amber  = Pair(Color.parseColor("#FEF3C7"), Color.parseColor("#B45309"))
        val cyan   = Pair(Color.parseColor("#CFFAFE"), Color.parseColor("#0E7490"))
        val teal   = Pair(Color.parseColor("#CCFBF1"), Color.parseColor("#0F766E"))

        // 3 rows × 3 cols = all 9 fields including Crop from form
        val cropName = (SurveySession.formData["crop_name"] as? String)
            ?: (SurveySession.formData["crop"] as? String)
            ?: "—"
        val green2 = Pair(Color.parseColor("#DCFCE7"), Color.parseColor("#15803D"))
        val grid = listOf(
            listOf(WCell("Latitude",  "%.6f".format(coords.lat), blue.first,   blue.second),
                WCell("Longitude", "%.6f".format(coords.lon), green.first,  green.second),
                WCell("Accuracy",  acc,                        purple.first, purple.second)),
            listOf(WCell("Altitude",  alt,                        amber.first,  amber.second),
                WCell("Date",      sdf.format(now),            blue.first,   blue.second),
                WCell("Time",      tdf.format(now),            purple.first, purple.second)),
            listOf(WCell("Emp ID",    uId,                        cyan.first,   cyan.second),
                WCell("Survey ID", sId,                        teal.first,   teal.second),
                WCell("Crop",      cropName,                   green2.first, green2.second))
        )

        val labelP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1D2B4D"); textSize = unit*2.1f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL) }
        val colonP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#6B7280"); textSize = unit*2.1f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL) }
        val valueP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#000000"); textSize = unit*2.4f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val iconR2 = unit*1.6f; val cellPad = unit*1.8f; val gap = unit*0.8f

        fun baselineY(midY: Float, p: Paint): Float { val fm = p.fontMetrics; return midY-(fm.ascent+fm.descent)/2f }

        grid.forEachIndexed { rowIdx, cells ->
            val midY = cardY + headerH + rowIdx * rowH + rowH * 0.5f
            cells.forEachIndexed { colIdx, cell ->
                if (cell.label.isEmpty()) return@forEachIndexed
                val cellLeft = cardX + colIdx * thirdW
                drawCell(canvas, cell, cellLeft, cellLeft+thirdW, cellPad, midY, iconR2, gap, labelP, colonP, valueP) { p: Paint -> baselineY(midY, p) }
            }
        }
    }

    private fun drawCell(
        canvas: Canvas,
        cell: WCell,
        cellLeft: Float,
        cellRight: Float,
        cellPad: Float,
        midY: Float,
        iconR: Float,
        gap: Float,
        labelP: Paint,
        colonP: Paint,
        valueP: Paint,
        baselineY: (Paint) -> Float
    ) {
        // Icon circle
        val iconCX = cellLeft + cellPad + iconR
        canvas.drawCircle(iconCX, midY, iconR,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cell.iconBg })
        canvas.drawCircle(iconCX, midY, iconR * 0.38f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cell.iconFg })

        // Available width for text after icon
        val textStart  = iconCX + iconR + gap
        val totalAvail = cellRight - textStart - cellPad * 0.5f

        val colonW = colonP.measureText(" : ")

        // Measure label to keep it fixed-ish; trim if needed
        val labelMaxW = totalAvail * 0.38f
        val labelStr  = clip(cell.label, labelP, labelMaxW)
        val labelW    = labelP.measureText(labelStr)

        val valueMaxW = totalAvail - labelW - colonW - gap * 0.5f
        val valueStr  = clip(cell.value, valueP, valueMaxW)

        var x = textStart
        canvas.drawText(labelStr, x, baselineY(labelP), labelP)
        x += labelW
        canvas.drawText(" : ", x, baselineY(colonP), colonP)
        x += colonW
        canvas.drawText(valueStr, x, baselineY(valueP), valueP)
    }

    /** Truncate [text] with "…" to fit within [maxW] pixels. */
    private fun clip(text: String, paint: Paint, maxW: Float): String {
        if (paint.measureText(text) <= maxW) return text
        var t = text
        while (t.isNotEmpty() && paint.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
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

    private fun updatePhotoUI() {
        val req = photoRequirements.getOrNull(photoIndex) ?: return
        tvPhotoLabel.text  = req.label
        tvInstruction.text = req.instruction ?: ""
        tvProgress.text    = "${photoIndex + 1} / ${photoRequirements.size}"
        tvInstruction.visibility = if (req.instruction != null) View.VISIBLE else View.GONE
    }

    override fun onDestroy() { super.onDestroy(); cameraExecutor.shutdown() }
}