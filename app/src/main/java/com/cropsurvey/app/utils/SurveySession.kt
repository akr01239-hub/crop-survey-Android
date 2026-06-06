package com.cropsurvey.app.utils

import android.content.Context
import android.content.SharedPreferences
import com.cropsurvey.app.models.CapturedPhoto
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Holds the currently active survey state in memory.
 * Acts like the Zustand surveyStore from the React Native version.
 *
 * Photo draft persistence: capturedPhotos are serialised to SharedPreferences
 * so they survive process death and are restored when the user re-opens a draft.
 */
object SurveySession {

    private const val PREFS_NAME   = "survey_draft_prefs"
    private const val KEY_PHOTOS   = "captured_photos"
    private const val KEY_SURVEY_ID = "draft_survey_id"

    private var prefs: SharedPreferences? = null

    /** Call once in Application.onCreate or at login to initialise storage. */
    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var currentSurveyId: String? = null
    var currentSurveyType: String? = null
    var currentCaseId: String = ""       // human-readable case ID shown on watermark (e.g. K2VATH5INI)
    var userId: String = ""          // internal UUID (set at login)
    var employeeId: String = ""      // FIX: human-readable employee ID shown on watermark
    var formData: MutableMap<String, Any?> = mutableMapOf()
    var polygonGeoJson: Map<String, Any?>? = null
    var polygonAreaHectares: Double? = null
    val capturedPhotos: MutableMap<String, CapturedPhoto> = mutableMapOf()

    // ── Persistence helpers ───────────────────────────────────────────────────

    /** Persist capturedPhotos to SharedPreferences so drafts survive restarts. */
    fun savePhotosDraft() {
        val p = prefs ?: return
        val arr = JSONArray()
        capturedPhotos.values.forEach { cp ->
            arr.put(JSONObject().apply {
                put("photoKey", cp.photoKey)
                put("localUri", cp.localUri)
                put("lat",      cp.lat)
                put("lon",      cp.lon)
                put("accuracy", cp.accuracy ?: JSONObject.NULL)
                put("uploaded", cp.uploaded)
            })
        }
        p.edit()
            .putString(KEY_PHOTOS,    arr.toString())
            .putString(KEY_SURVEY_ID, currentSurveyId ?: "")
            .apply()
    }

    /**
     * Restore capturedPhotos from SharedPreferences for the given surveyId.
     * Only restores if the stored draft belongs to the same survey.
     * Skips entries whose localUri file no longer exists on disk (e.g. cache cleared).
     */
    fun restorePhotosDraft(surveyId: String) {
        val p = prefs ?: return
        val storedId = p.getString(KEY_SURVEY_ID, "") ?: ""
        if (storedId != surveyId) return          // different survey — don't restore

        val json = p.getString(KEY_PHOTOS, null) ?: return
        try {
            val arr = JSONArray(json)
            for (idx in 0 until arr.length()) {
                val o = arr.getJSONObject(idx)
                val key = o.getString("photoKey")
                if (capturedPhotos.containsKey(key)) continue  // already in session

                val uri = o.getString("localUri")
                // Only restore if file still exists on device cache
                if (!uri.startsWith("http") && !java.io.File(uri).exists()) continue

                capturedPhotos[key] = CapturedPhoto(
                    photoKey = key,
                    localUri = uri,
                    lat      = o.getDouble("lat"),
                    lon      = o.getDouble("lon"),
                    accuracy = if (o.isNull("accuracy")) null else o.getDouble("accuracy").toFloat(),
                    uploaded = o.getBoolean("uploaded")
                )
            }
        } catch (_: Exception) { /* corrupt prefs — ignore */ }
    }

    /** Clear persisted draft photos (call after successful submit). */
    fun clearPhotosDraft() {
        prefs?.edit()?.remove(KEY_PHOTOS)?.remove(KEY_SURVEY_ID)?.apply()
    }

    /** Call after submit to delete photo files from filesDir. Pass application context. */
    fun clearPhotoFiles(ctx: Context) {
        clearPhotosDraft()
        try {
            val dir = File(ctx.filesDir, "survey_photos")
            dir.listFiles()?.forEach { file -> file.delete() }
        } catch (_: Exception) {}
    }

    /**
     * Start a BRAND NEW survey (first time fill-in).
     * Only clears the photo draft if the stored draft belongs to a different survey.
     * For CHM visits 2-5, preserves carry-over fields pre-populated from previous visit.
     */
    fun startSurvey(type: String, surveyId: String) {
        val storedId = prefs?.getString(KEY_SURVEY_ID, "") ?: ""

        // Always preserve farmer verification across the reset — it was set by
        // FarmerVerificationActivity and must survive into the form fragment.
        val savedFarmerPhone    = formData["farmer_phone"]?.toString() ?: ""
        val savedFarmerVerified = formData["farmer_verified"]?.toString() ?: ""

        // Save any CHM carry-over data that was pre-filled before this call
        val savedCarryOver = if (type == "CHM" && formData.isNotEmpty()) {
            formData.toMap()  // snapshot everything pre-filled
        } else emptyMap()

        currentSurveyId   = surveyId
        currentSurveyType = type
        formData          = mutableMapOf()
        polygonGeoJson    = null
        polygonAreaHectares = null
        capturedPhotos.clear()
        // Only wipe the stored draft if it belongs to a DIFFERENT survey
        if (storedId != surveyId) clearPhotosDraft()

        // Restore CHM carry-over fields AFTER the reset so they aren't lost
        if (savedCarryOver.isNotEmpty()) {
            formData.putAll(savedCarryOver)
        }

        // Always restore farmer verification — it must never be wiped by a survey reset
        if (savedFarmerVerified == "yes" && savedFarmerPhone.isNotEmpty()) {
            formData["farmer_phone"]    = savedFarmerPhone
            formData["farmer_verified"] = "yes"
        }
    }

    /**
     * Resume a DRAFT survey — restores formData AND any previously captured
     * photos from SharedPreferences so the user continues where they left off.
     */
    fun restoreForDraftEdit(type: String, surveyId: String, data: Map<String, Any?>) {
        currentSurveyId   = surveyId
        currentSurveyType = type
        formData          = data.toMutableMap()
        restorePhotosDraft(surveyId)   // ← reload locally cached photo paths
    }

    /**
     * Restore session for editing a REJECTED survey.
     * Does NOT clear capturedPhotos so existing server-side photos aren't orphaned.
     * The photo upload route already handles "replace by key" so re-taking a photo
     * deletes the old one automatically.
     */
    fun restoreForRejectedEdit(type: String, surveyId: String, data: Map<String, Any?>) {
        currentSurveyId   = surveyId
        currentSurveyType = type
        formData          = data.toMutableMap()
        restorePhotosDraft(surveyId)   // ← also restore any locally retaken photos
    }

    fun updateFormData(data: Map<String, Any?>) {
        formData.putAll(data)
    }

    /**
     * Add or REPLACE a photo for the given slot key.
     * Calling this for an existing key replaces it — tapping Capture on a slot
     * that already has a photo will replace it, never add a duplicate.
     * Persists the updated map to SharedPreferences immediately.
     */
    fun addCapturedPhoto(photo: CapturedPhoto) {
        capturedPhotos[photo.photoKey] = photo   // map key guarantees one entry per slot
        savePhotosDraft()                        // persist so draft survives restart
    }

    fun markPhotoUploaded(photoKey: String) {
        capturedPhotos[photoKey]?.let {
            capturedPhotos[photoKey] = it.copy(uploaded = true)
        }
    }

    fun allPhotosUploaded(surveyType: String): Boolean {
        val required = com.cropsurvey.app.config.AppConfig.getPhotosForType(surveyType)
        return required.all { req -> capturedPhotos[req.key]?.uploaded == true }
    }

    fun getPhotoCount(surveyType: String): Pair<Int, Int> {
        val required = com.cropsurvey.app.config.AppConfig.getPhotosForType(surveyType)
        val captured = required.count { req -> capturedPhotos.containsKey(req.key) }
        return Pair(captured, required.size)
    }

    /**
     * Called when survey is SUBMITTED — clears polygon buffer so it's temporary.
     * Draft and rejected surveys keep polygonGeoJson so buffer persists.
     */
    fun clearPolygonOnSubmit() {
        polygonGeoJson = null
    }

    /**
     * Called when starting a rejected survey re-edit — polygon is re-loaded
     * from form_data so the buffer is automatically recreated.
     */
    fun restorePolygonForResubmit(geoJson: Map<String, Any?>) {
        polygonGeoJson = geoJson
    }

    /**
     * Load the polygon from a previous CHM visit into the session so that
     * ChmBufferCheckActivity can display it and GeoBufferHelper can evaluate
     * the 50 m buffer without requiring the surveyor to re-draw anything.
     *
     * Priority:
     *   1. Top-level polygon_geojson field on the Survey object (most reliable)
     *   2. polygon_geojson stored inside form_data (fallback)
     */
    @Suppress("UNCHECKED_CAST")
    fun loadPolygonFromPreviousVisit(surveyPolygon: Map<String, Any?>?, formDataPolygon: Any?) {
        when {
            surveyPolygon != null -> polygonGeoJson = surveyPolygon
            formDataPolygon is Map<*, *> -> {
                polygonGeoJson = formDataPolygon as? Map<String, Any?>
            }
        }
    }

    /**
     * Called on LOGOUT — clears in-memory session state but deliberately does NOT
     * call clearPhotosDraft(). Draft photos survive logout so the surveyor can
     * continue from where they left off after logging back in.
     * Photos are only wiped on successful submit via clearPhotosDraft().
     */
    fun reset() {
        currentSurveyId   = null
        currentSurveyType = null
        currentCaseId     = ""
        userId            = ""
        employeeId        = ""
        formData          = mutableMapOf()
        polygonGeoJson    = null
        polygonAreaHectares = null
        capturedPhotos.clear()
        // ⚠️ Do NOT call clearPhotosDraft() here — draft photos must survive logout/login
    }

    // ── CHM Multi-Visit state ─────────────────────────────────────────────────
    /** Which visit number (1-5) is currently being filled */
    var chmVisitNumber: Int = 1
    /** The shared case_id for all CHM visits of a single farmer/field */
    var chmCaseId: String? = null

    /** Clear session state for starting a new CHM visit */
    fun clearSession() {
        formData = mutableMapOf()
        polygonGeoJson = null
        polygonAreaHectares = null
        capturedPhotos.clear()
        chmVisitNumber = 1
        chmCaseId = null
    }
}