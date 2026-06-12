package com.cropsurvey.app.queue

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cropsurvey.app.R
import com.cropsurvey.app.models.GpsCoords
import com.cropsurvey.app.models.QueueItem
import com.cropsurvey.app.models.UpdateSurveyRequest
import com.cropsurvey.app.network.ApiClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ─── QueueManager (SharedPreferences-based offline queue) ─────────────────────

object QueueManager {

    private const val PREF_NAME = "offline_queue"
    private const val KEY_ITEMS = "queue_items"
    private val gson = Gson()

    fun getItems(context: Context): MutableList<QueueItem> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ITEMS, "[]") ?: "[]"
        val type = object : TypeToken<MutableList<QueueItem>>() {}.type
        return try { gson.fromJson(json, type) } catch (e: Exception) { mutableListOf() }
    }

    private fun saveItems(context: Context, items: List<QueueItem>) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_ITEMS, gson.toJson(items)).apply()
    }

    fun enqueueFormUpdate(context: Context, surveyId: String, formData: Map<String, Any?>) {
        val items = getItems(context)
        val item = QueueItem(
            id = UUID.randomUUID().toString(),
            surveyId = surveyId,
            type = "form",
            payload = formData,
            queuedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())
        )
        items.add(item)
        saveItems(context, items)
    }

    fun enqueueSubmit(context: Context, surveyId: String) {
        val items = getItems(context)
        // Avoid duplicate submit entries for the same survey
        if (items.any { it.surveyId == surveyId && it.type == "submit" }) return
        val item = QueueItem(
            id = UUID.randomUUID().toString(),
            surveyId = surveyId,
            type = "submit",
            payload = emptyMap(),
            queuedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())
        )
        items.add(item)
        saveItems(context, items)
    }

    fun enqueuePhoto(context: Context, surveyId: String, photoKey: String, label: String, localUri: String, coords: GpsCoords) {
        val items = getItems(context)
        val item = QueueItem(
            id = UUID.randomUUID().toString(),
            surveyId = surveyId,
            type = "photo",
            payload = mapOf("photoKey" to photoKey, "label" to label, "lat" to coords.lat, "lon" to coords.lon),
            photoLocalUri = localUri,
            queuedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())
        )
        items.add(item)
        saveItems(context, items)
    }

    fun removeItem(context: Context, id: String) {
        val items = getItems(context)
        items.removeAll { it.id == id }
        saveItems(context, items)
    }

    fun incrementRetry(context: Context, id: String) {
        val items = getItems(context)
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) {
            items[idx] = items[idx].copy(retryCount = items[idx].retryCount + 1)
            saveItems(context, items)
        }
    }

    fun getPendingCount(context: Context): Int {
        return getItems(context).count { it.retryCount < com.cropsurvey.app.config.AppConfig.MAX_RETRY_COUNT }
    }

    fun flush(context: Context) {
        val items = getItems(context).filter { it.retryCount < com.cropsurvey.app.config.AppConfig.MAX_RETRY_COUNT }
        if (items.isEmpty()) return

        // Fire and forget
        kotlinx.coroutines.GlobalScope.launch {
            for (item in items) {
                try {
                    val success = when (item.type) {
                        "form" -> {
                            @Suppress("UNCHECKED_CAST")
                            val fd = item.payload as? Map<String, Any?> ?: continue
                            val res = ApiClient.service.updateSurvey(item.surveyId, UpdateSurveyRequest(fd))
                            res.isSuccessful
                        }
                        "photo" -> {
                            val uri = item.photoLocalUri ?: continue
                            val file = File(uri)
                            if (!file.exists()) { removeItem(context, item.id); continue }
                            val photoKey = item.payload["photoKey"]?.toString() ?: continue
                            val label = item.payload["label"]?.toString() ?: continue
                            val lat = (item.payload["lat"] as? Double)?.toString() ?: "0"
                            val lon = (item.payload["lon"] as? Double)?.toString() ?: "0"
                            val capturedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())

                            val mimeType = if (file.name.endsWith(".mp4", ignoreCase = true)) "video/mp4" else "image/jpeg"
                            val photoPart = MultipartBody.Part.createFormData("photo", file.name, file.asRequestBody(mimeType.toMediaType()))
                            val res = ApiClient.service.uploadPhoto(
                                surveyId = item.surveyId,
                                photo = photoPart,
                                photoKey = photoKey.toRequestBody("text/plain".toMediaType()),
                                label = label.toRequestBody("text/plain".toMediaType()),
                                lat = lat.toRequestBody("text/plain".toMediaType()),
                                lon = lon.toRequestBody("text/plain".toMediaType()),
                                accuracy = "0".toRequestBody("text/plain".toMediaType()),
                                capturedAt = capturedAt.toRequestBody("text/plain".toMediaType())
                            )
                            res.isSuccessful
                        }
                        "submit" -> {
                            val res = ApiClient.service.submitSurvey(item.surveyId)
                            res.isSuccessful
                        }
                        else -> false
                    }

                    if (success) removeItem(context, item.id)
                    else incrementRetry(context, item.id)

                } catch (e: Exception) {
                    incrementRetry(context, item.id)
                }
            }
        }
    }
}

// ─── QueueActivity ─────────────────────────────────────────────────────────────

class QueueActivity : AppCompatActivity() {

    private lateinit var rvQueue: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvOnlineStatus: TextView
    private lateinit var btnSendAll: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_queue)

        rvQueue          = findViewById(R.id.rv_queue)
        tvEmpty          = findViewById(R.id.tv_empty)
        tvOnlineStatus   = findViewById(R.id.tv_online_status)
        btnSendAll       = findViewById(R.id.btn_send_all)
        progressBar      = findViewById(R.id.progress_bar)

        rvQueue.layoutManager = LinearLayoutManager(this)

        loadQueue()

        btnSendAll.setOnClickListener {
            progressBar.visibility = View.VISIBLE
            btnSendAll.isEnabled = false
            QueueManager.flush(this)
            android.os.Handler(mainLooper).postDelayed({
                progressBar.visibility = View.GONE
                btnSendAll.isEnabled = true
                loadQueue()
                Toast.makeText(this, "Upload triggered", Toast.LENGTH_SHORT).show()
            }, 3000)
        }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }
    }

    private fun loadQueue() {
        val items = QueueManager.getItems(this).filter { it.retryCount < com.cropsurvey.app.config.AppConfig.MAX_RETRY_COUNT }
        if (items.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            rvQueue.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            rvQueue.visibility = View.VISIBLE
            rvQueue.adapter = QueueAdapter(items)
        }
    }
}

// ─── QueueAdapter ──────────────────────────────────────────────────────────────

class QueueAdapter(private val items: List<QueueItem>) : RecyclerView.Adapter<QueueAdapter.VH>() {
    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvType: TextView    = view.findViewById(R.id.tv_item_type)
        val tvSurvey: TextView  = view.findViewById(R.id.tv_survey_id)
        val tvRetry: TextView   = view.findViewById(R.id.tv_retry_count)
        val tvQueued: TextView  = view.findViewById(R.id.tv_queued_at)
    }
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val v = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_queue, parent, false)
        return VH(v)
    }
    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = items[pos]
        h.tvType.text   = if (item.type == "photo") "📷 Photo" else "📋 Form"
        h.tvSurvey.text = item.surveyId.take(12) + "..."
        h.tvRetry.text  = "Retry #${item.retryCount}"
        h.tvQueued.text = item.queuedAt.take(16)
    }
    override fun getItemCount() = items.size
}