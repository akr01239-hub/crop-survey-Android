package com.cropsurvey.app.models

import com.google.gson.annotations.SerializedName

// ─── User ─────────────────────────────────────────────────────────────────────

data class User(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String? = null,
    val role: String = "surveyor",
    val agency: String? = null,
    @SerializedName("ic_name") val icName: String? = null,
    @SerializedName("is_active") val isActive: Boolean? = true,
    @SerializedName("employee_id") val employeeId: String? = null  // FIX: employee ID for watermark
)

// ─── Survey ───────────────────────────────────────────────────────────────────

data class Survey(
    val id: String = "",
    @SerializedName("case_id") val caseId: String = "",
    @SerializedName("survey_type") val surveyType: String = "",
    @SerializedName("user_id") val userId: String = "",
    val status: String = "draft",
    @SerializedName("form_data") val formData: Map<String, Any?> = emptyMap(),
    @SerializedName("polygon_geojson") val polygonGeoJson: Map<String, Any?>? = null,
    @SerializedName("submitted_at") val submittedAt: String? = null,
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("updated_at") val updatedAt: String = "",
    @SerializedName("rejection_reason") val rejectionReason: String? = null
)

// ─── Survey Photo ─────────────────────────────────────────────────────────────

data class SurveyPhoto(
    val id: String = "",
    @SerializedName("survey_id") val surveyId: String = "",
    @SerializedName("photo_key") val photoKey: String = "",
    val label: String = "",
    @SerializedName("storage_url") val storageUrl: String = "",
    @SerializedName("signed_url") val signedUrl: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val altitude: Double? = null,
    val accuracy: Double? = null,
    @SerializedName("captured_at") val capturedAt: String? = null
)

// ─── API Request/Response Models ──────────────────────────────────────────────

data class CreateSurveyRequest(
    @SerializedName("survey_type") val surveyType: String,
    @SerializedName("chm_case_id") val chmCaseId: String? = null,
    @SerializedName("chm_visit_number") val chmVisitNumber: Int? = null
)

data class UpdateSurveyRequest(
    @SerializedName("form_data") val formData: Map<String, Any?>,
    @SerializedName("polygon_geojson") val polygonGeoJson: Map<String, Any?>? = null
)

data class RejectSurveyRequest(
    val reason: String
)

data class SurveyListResponse(
    val data: List<Survey>,
    val pagination: Pagination
)

data class Pagination(
    val page: Int,
    val limit: Int,
    val total: Int,
    val pages: Int
)

data class OtpSendRequest(
    val phone: String
)

data class OtpVerifyRequest(
    val phone: String,
    val token: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val address: String? = null,
    val device_id: String? = null,
    val is_mock: Boolean = false
)

data class AuthResponse(
    val session: SessionData?,
    val user: User?,
    @SerializedName("locked_device_id") val lockedDeviceId: String? = null
)

data class SessionData(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String
)

// ─── GPS ──────────────────────────────────────────────────────────────────────

data class GpsCoords(
    val lat: Double,
    val lon: Double,
    val accuracy: Float? = null,
    val altitude: Double? = null,
    val isMocked: Boolean = false
)

// ─── Captured Photo (local) ───────────────────────────────────────────────────

data class CapturedPhoto(
    val photoKey: String,
    val localUri: String,
    val lat: Double,
    val lon: Double,
    val accuracy: Float?,
    val uploaded: Boolean = false
)

// ─── Offline Queue Item ───────────────────────────────────────────────────────

data class QueueItem(
    val id: String,
    val surveyId: String,
    val type: String,
    val payload: Map<String, Any?>,
    val photoLocalUri: String? = null,
    val retryCount: Int = 0,
    val queuedAt: String
)