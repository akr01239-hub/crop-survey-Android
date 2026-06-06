package com.cropsurvey.app.models

import com.google.gson.annotations.SerializedName

/**
 * Represents one visit record within a CHM multi-visit case.
 *
 * Backend stores each visit as a separate survey row keyed by
 * (case_id, visit_number). The API endpoints are:
 *
 *   GET  surveys/chm/visits?case_id={id}          → List<ChmVisit>
 *   POST surveys/chm/visits                        → create visit-1 (returns ChmVisit)
 *   POST surveys/{survey_id}/submit                → reused for CHM visits too
 */
data class ChmVisit(
    /** UUID of the survey row for this visit */
    @SerializedName("survey_id")   val surveyId: String = "",

    /** Shared case identifier linking all visits for one farmer/field */
    @SerializedName("case_id")     val caseId: String = "",

    /** 1–5 */
    @SerializedName("visit_number") val visitNumber: Int = 1,

    /** draft | submitted | approved | rejected */
    val status: String = "draft",

    @SerializedName("form_data")   val formData: Map<String, Any?> = emptyMap(),

    @SerializedName("submitted_at") val submittedAt: String? = null,
    @SerializedName("approved_at")  val approvedAt: String? = null,
    @SerializedName("created_at")   val createdAt: String = "",
    @SerializedName("updated_at")   val updatedAt: String = "",
    @SerializedName("rejection_reason") val rejectionReason: String? = null
)

/** Request body to create a new CHM visit (visit 1 or subsequent) */
data class CreateChmVisitRequest(
    @SerializedName("case_id")      val caseId: String?,   // null for first visit
    @SerializedName("visit_number") val visitNumber: Int,
    @SerializedName("survey_type")  val surveyType: String = "CHM"
)
