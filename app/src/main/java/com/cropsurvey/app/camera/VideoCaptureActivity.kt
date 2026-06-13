package com.cropsurvey.app.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cropsurvey.app.utils.GpsHelper
import com.cropsurvey.app.utils.SurveySession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.cropsurvey.app.R
import java.io.File

/**
 * Records a short (max 30s) video using the same in-app CameraX camera used
 * for photo capture, so it works on every device regardless of whether a
 * separate camera app is installed/visible (avoids "No camera app available").
 *
 * Returns the absolute path of the recorded file via RESULT_OK extra
 * [EXTRA_RESULT_PATH], or RESULT_CANCELED if the user backs out.
 */
class VideoCaptureActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RESULT_PATH = "result_path"
        const val MAX_DURATION_MS = 30_000L
        private const val PERMISSION_CODE = 5301
    }

    private lateinit var previewView: PreviewView
    private lateinit var btnRecord: ImageButton
    private lateinit var btnClose: ImageButton
    private lateinit var tvTimer: TextView
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar

    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var isRecording = false

    private val handler = Handler(Looper.getMainLooper())
    private var remainingMs = MAX_DURATION_MS
    private val tickRunnable = object : Runnable {
        override fun run() {
            remainingMs -= 1000
            if (remainingMs <= 0) {
                tvTimer.text = "00:00"
                stopRecording()
            } else {
                val secs = (remainingMs / 1000).toInt()
                tvTimer.text = String.format("00:%02d", secs)
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_capture)

        previewView = findViewById(R.id.preview_view)
        btnRecord   = findViewById(R.id.btn_record)
        btnClose    = findViewById(R.id.btn_close)
        tvTimer     = findViewById(R.id.tv_timer)
        tvStatus    = findViewById(R.id.tv_status)
        progressBar = findViewById(R.id.progress_bar)

        btnClose.setOnClickListener {
            if (isRecording) stopRecording(cancelled = true)
            setResult(RESULT_CANCELED)
            finish()
        }
        btnRecord.setOnClickListener {
            if (isRecording) stopRecording() else startRecording()
        }

        val needed = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (needed.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, needed, PERMISSION_CODE)
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == PERMISSION_CODE && results.all { it == PackageManager.PERMISSION_GRANTED }) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera and microphone permission required to record", Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun startCamera() {
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

        ProcessCameraProvider.getInstance(this).addListener({
            val cp = ProcessCameraProvider.getInstance(this).get()

            val preview = androidx.camera.core.Preview.Builder().build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val qualitySelector = QualitySelector.fromOrderedList(
                listOf(Quality.SD, Quality.LOWEST),
                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
            )
            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            try {
                cp.unbindAll()
                cp.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, videoCapture)
            } catch (e: Exception) {
                Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startRecording() {
        val vc = videoCapture ?: return
        val outFile = File(cacheDir, "dispute_${System.currentTimeMillis()}.mp4")
        val outputOptions = FileOutputOptions.Builder(outFile).build()

        activeRecording = vc.output
            .prepareRecording(this, outputOptions)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        isRecording = true
                        remainingMs = MAX_DURATION_MS
                        tvStatus.text = "Recording… tap to stop"
                        btnRecord.setImageResource(android.R.drawable.ic_media_pause)
                        handler.postDelayed(tickRunnable, 1000)
                    }
                    is VideoRecordEvent.Finalize -> {
                        isRecording = false
                        handler.removeCallbacks(tickRunnable)
                        if (!event.hasError()) {
                            tvStatus.text = "Adding authenticity stamp…"
                            progressBar.visibility = View.VISIBLE
                            btnRecord.isEnabled = false
                            lifecycleScope.launch {
                                val finalPath = stampAndGetPath(outFile)
                                val resultIntent = android.content.Intent()
                                resultIntent.putExtra(EXTRA_RESULT_PATH, finalPath)
                                setResult(RESULT_OK, resultIntent)
                                finish()
                            }
                        } else {
                            Toast.makeText(this, "Recording failed: ${event.error}", Toast.LENGTH_SHORT).show()
                            tvStatus.text = "Tap to start recording (30 sec)"
                            tvTimer.text = "00:30"
                            btnRecord.setImageResource(android.R.drawable.presence_video_online)
                        }
                    }
                    else -> {}
                }
            }
    }

    /**
     * Burns a GPS/timestamp/employee-ID stamp into the recorded video.
     * On any failure (unsupported codec, low memory, etc.) falls back to the
     * original unstamped file so the recording is never lost.
     */
    private suspend fun stampAndGetPath(originalFile: File): String = withContext(Dispatchers.Default) {
        try {
            val coords = GpsHelper.getCurrentLocation(this@VideoCaptureActivity)
                ?: com.cropsurvey.app.models.GpsCoords(
                    (SurveySession.formData["capture_lat"] as? Double) ?: 0.0,
                    (SurveySession.formData["capture_lon"] as? Double) ?: 0.0
                )
            val place = StampUtils.reverseGeocode(this@VideoCaptureActivity, coords.lat, coords.lon)
            val empId = SurveySession.employeeId.ifEmpty { "—" }
            val caseId = SurveySession.currentCaseId.ifEmpty { "—" }

            // Get frame dimensions from the recorded file to size the overlay correctly
            val extractor = android.media.MediaExtractor()
            extractor.setDataSource(originalFile.absolutePath)
            var w = 1280; var h = 720
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                if (fmt.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    w = fmt.getInteger(android.media.MediaFormat.KEY_WIDTH)
                    h = fmt.getInteger(android.media.MediaFormat.KEY_HEIGHT)
                    break
                }
            }
            extractor.release()

            val stampBmp = StampUtils.createVideoStampBitmap(w, h, coords, place, empId, caseId)
            val stampedFile = File(cacheDir, "stamped_${originalFile.name}")
            VideoStampTranscoder.stampVideo(originalFile, stampedFile, stampBmp)
            originalFile.delete()
            stampedFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("VideoStamp", "Stamping failed, using unstamped video", e)
            originalFile.absolutePath
        }
    }

    private fun stopRecording(cancelled: Boolean = false) {
        activeRecording?.stop()
        activeRecording = null
        handler.removeCallbacks(tickRunnable)
        if (cancelled) isRecording = false
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(tickRunnable)
        activeRecording?.stop()
    }
}
