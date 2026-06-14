package com.cropsurvey.app.survey.cls


import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.cropsurvey.app.R
import com.cropsurvey.app.config.AppConfig
import com.cropsurvey.app.i18n.TranslatedDropdown
import com.cropsurvey.app.models.CapturedPhoto
import com.cropsurvey.app.network.ApiClient
import com.cropsurvey.app.queue.QueueManager
import com.cropsurvey.app.utils.GpsHelper
import com.cropsurvey.app.utils.SurveySession
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.cropsurvey.app.guide.AiGuideOverlay

/**
 * CLS Form – section behaviour mirrors CCEFormFragment exactly:
 *  - All sections collapsed on new survey; expanded on restore
 *  - Section complete → green header + green ✓ badge (only when ALL required fields filled)
 *  - Section incomplete + nextAttempted → red header + red ✗ badge
 *  - Section untouched / Next not yet attempted → grey header, no badge
 *  - NO per-field green tick icons (only label turns green when filled)
 */
class CLSFormFragment : Fragment() {

    // ── Section 1: Basic Information ──────────────────────────────
    private lateinit var etSurveyId: EditText
    private lateinit var spYear: Spinner
    private lateinit var spSeason: Spinner
    private lateinit var spScheme: Spinner
    private lateinit var etOtherScheme: EditText
    private lateinit var layoutOtherScheme: View

    // ── Section 2: Location ───────────────────────────────────────
    private lateinit var spState: Spinner
    private lateinit var spDistrict: Spinner
    private lateinit var spTehsil: Spinner
    private lateinit var etRevenueCircle: EditText
    private lateinit var etGramPanchayat: EditText
    private lateinit var etVillage: EditText

    // ── Section 3: Crop & Survey ──────────────────────────────────
    private lateinit var etSurveyIntimationNo: EditText
    private lateinit var spCropName: Spinner
    private lateinit var spInsuranceUnit: Spinner
    private lateinit var etOtherCrop: EditText
    private lateinit var layoutOtherCrop: View
    private lateinit var spAnyDisease: Spinner
    private lateinit var etDiseaseName: EditText
    private lateinit var layoutDiseaseName: View

    // ── Section 4: Farmer Information ────────────────────────────
    private lateinit var etFarmerName: EditText
    private lateinit var etFarmerMobile: EditText
    private lateinit var etFarmerAppNo: EditText
    private lateinit var spFarmerAvailable: Spinner
    private lateinit var layoutFarmerUnavailable: View
    private lateinit var etRelationWithFarmer: EditText
    private lateinit var etRepresentativeName: EditText
    private lateinit var etRepresentativeMobile: EditText

    // ── Section 5: Key Dates ──────────────────────────────────────
    private lateinit var etSurveyDate: EditText
    private lateinit var etSowingDate: EditText
    private lateinit var etDateOfIntimation: EditText
    private lateinit var etDateOfLoss: EditText

    // ── Section 6: Loss Details ───────────────────────────────────
    private lateinit var spCauseOfEvent: Spinner
    private lateinit var etOtherCause: EditText
    private lateinit var layoutOtherCause: View
    private lateinit var spCropStage: Spinner
    private lateinit var spCroppingPattern: Spinner
    private lateinit var spPostHarvest: Spinner
    private lateinit var layoutPostHarvest: View
    private lateinit var etHarvestDate: EditText
    private lateinit var spCropSituationField: Spinner
    private lateinit var etKhasraNo: EditText
    private lateinit var etFieldAreaPolygon: EditText
    private lateinit var etTotalLandArea: EditText
    private lateinit var etInsuredArea: EditText
    private lateinit var etAreaAffectedPct: EditText
    private lateinit var etLossPct: EditText
    private lateinit var etRecoveryRate: EditText
    private lateinit var spOnfieldCondition: Spinner

    // ── Section 7: Government Officer ────────────────────────────
    private lateinit var etOfficerName: EditText
    private lateinit var etOfficerMobile: EditText
    private lateinit var etOfficerDesignation: EditText

    // ── Section 8: Remarks & GPS ──────────────────────────────────
    private lateinit var spDisputeIfAny: Spinner
    private lateinit var layoutDisputeRecording: View
    private lateinit var btnRecordDispute: Button
    private lateinit var tvDisputeRecordingStatus: TextView
    private lateinit var etRemarks: EditText
    private lateinit var tvGpsCoords: TextView

    // ── Label TextViews ───────────────────────────────────────────
    private lateinit var lblSurveyId: TextView
    private lateinit var lblYear: TextView
    private lateinit var lblSeason: TextView
    private lateinit var lblScheme: TextView
    private lateinit var lblOtherScheme: TextView
    private lateinit var lblState: TextView
    private lateinit var lblDistrict: TextView
    private lateinit var lblTehsil: TextView
    private lateinit var lblRevenueCircle: TextView
    private lateinit var lblGramPanchayat: TextView
    private lateinit var lblVillage: TextView
    private lateinit var lblSurveyIntimationNo: TextView
    private lateinit var lblCropName: TextView
    private lateinit var lblInsuranceUnit: TextView
    private lateinit var lblOtherCrop: TextView
    private lateinit var lblAnyDisease: TextView
    private lateinit var lblDiseaseName: TextView
    private lateinit var lblFarmerName: TextView
    private lateinit var lblFarmerMobile: TextView
    private lateinit var lblFarmerAppNo: TextView
    private lateinit var lblSurveyDate: TextView
    private lateinit var lblSowingDate: TextView
    private lateinit var lblDateOfIntimation: TextView
    private lateinit var lblDateOfLoss: TextView
    private lateinit var lblCauseOfEvent: TextView
    private lateinit var lblOtherCause: TextView
    private lateinit var lblCropStage: TextView
    private lateinit var lblCroppingPattern: TextView
    private lateinit var lblKhasraNo: TextView
    private lateinit var lblFieldAreaPolygon: TextView
    private lateinit var lblTotalLandArea: TextView
    private lateinit var lblInsuredArea: TextView
    private lateinit var lblAreaAffectedPct: TextView
    private lateinit var lblLossPct: TextView
    private lateinit var lblRecoveryRate: TextView
    private lateinit var lblOnfieldCondition: TextView
    private lateinit var lblFarmerAvailable: TextView
    private lateinit var lblRelationWithFarmer: TextView
    private lateinit var lblRepresentativeName: TextView
    private lateinit var lblRepresentativeMobile: TextView
    private lateinit var lblPostHarvest: TextView
    private lateinit var lblHarvestDate: TextView
    private lateinit var lblCropSituationField: TextView
    private lateinit var lblDisputeIfAny: TextView
    private lateinit var lblOfficerName: TextView
    private lateinit var lblOfficerMobile: TextView
    private lateinit var lblOfficerDesignation: TextView
    private lateinit var lblRemarks: TextView

    // ── Collapsible section state ─────────────────────────────────
    private val sectionHeaders  = mutableMapOf<Int, View>()
    private val sectionBodies   = mutableMapOf<Int, View>()
    private val sectionArrows   = mutableMapOf<Int, TextView>()
    private val sectionChecks   = mutableMapOf<Int, ImageView>()
    private val sectionExpanded = mutableMapOf<Int, Boolean>()
    private val sectionTouched  = mutableMapOf<Int, Boolean>()
    private var nextAttempted   = false

    private var states       = listOf<String>()
    private var districts    = listOf<String>()
    private var subDistricts = listOf<String>()
    private var isRestoring  = false

    private val recordDisputeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val path = result.data?.getStringExtra(com.cropsurvey.app.camera.VideoCaptureActivity.EXTRA_RESULT_PATH)
            if (path != null) {
                tvDisputeRecordingStatus.text = "Uploading…"
                tvDisputeRecordingStatus.setTextColor(android.graphics.Color.parseColor("#2563EB"))
                uploadDisputeRecording(File(path))
            }
        } else {
            Toast.makeText(requireContext(), "Recording cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Uploads the recorded video through the same surveys/{id}/photos
     * pipeline used for photos — stamped with the survey's GPS coords and
     * the capture timestamp — so it shows up in the Photos tab/count and can
     * be played back on the review dashboard.
     */
    private fun uploadDisputeRecording(videoFile: File) {
        // IMPORTANT: surveys/{id}/photos needs the internal UUID, NOT the
        // short display Survey ID (Docket-ID/case_id) shown in etSurveyId.
        val surveyId = SurveySession.currentSurveyId
            ?: activity?.intent?.getStringExtra("survey_id")
        if (surveyId == null) {
            tvDisputeRecordingStatus.text = "Recorded (will upload on save)"
            tvDisputeRecordingStatus.setTextColor(android.graphics.Color.parseColor("#94A3B8"))
            SurveySession.formData["dispute_recording_uri"] = videoFile.absolutePath
            return
        }

        val disputeLabel = getString(R.string.field_dispute_recording)
        lifecycleScope.launch {
            try {
                // Move from cache to filesDir so it survives cache cleanup until uploaded.
                val videoDir = File(requireContext().filesDir, "survey_videos").also { it.mkdirs() }
                val outFile = File(videoDir, videoFile.name)
                videoFile.copyTo(outFile, overwrite = true)
                videoFile.delete()

                val lat = (SurveySession.formData["capture_lat"] as? Double) ?: 0.0
                val lon = (SurveySession.formData["capture_lon"] as? Double) ?: 0.0
                val capturedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())

                val videoPart = MultipartBody.Part.createFormData(
                    "photo", outFile.name, outFile.asRequestBody("video/mp4".toMediaType())
                )
                val res = ApiClient.service.uploadPhoto(
                    surveyId   = surveyId,
                    photo      = videoPart,
                    photoKey   = "dispute_recording".toRequestBody("text/plain".toMediaType()),
                    label      = disputeLabel.toRequestBody("text/plain".toMediaType()),
                    lat        = lat.toString().toRequestBody("text/plain".toMediaType()),
                    lon        = lon.toString().toRequestBody("text/plain".toMediaType()),
                    accuracy   = "0".toRequestBody("text/plain".toMediaType()),
                    capturedAt = capturedAt.toRequestBody("text/plain".toMediaType())
                )

                if (res.isSuccessful) {
                    SurveySession.addCapturedPhoto(
                        CapturedPhoto("dispute_recording", outFile.absolutePath, lat, lon, 0f, uploaded = true)
                    )
                    SurveySession.formData["dispute_recording_uri"] = outFile.absolutePath
                    tvDisputeRecordingStatus.text = "Recorded & uploaded ✓"
                    tvDisputeRecordingStatus.setTextColor(android.graphics.Color.parseColor("#16A34A"))
                } else {
                    val errBody = try { res.errorBody()?.string() } catch (_: Exception) { null }
                    android.util.Log.e("DisputeUpload", "HTTP ${res.code()} ${res.message()}: $errBody")
                    tvDisputeRecordingStatus.text = "Upload failed (HTTP ${res.code()}) — retrying automatically"
                    tvDisputeRecordingStatus.setTextColor(android.graphics.Color.parseColor("#F59E0B"))
                    SurveySession.formData["dispute_recording_uri"] = outFile.absolutePath
                    QueueManager.enqueuePhoto(
                        requireContext(), surveyId, "dispute_recording", disputeLabel,
                        outFile.absolutePath, com.cropsurvey.app.models.GpsCoords(lat, lon)
                    )
                }
                (activity as? com.cropsurvey.app.survey.SurveyTabsActivity)?.refreshPhotoCount()
            } catch (e: Exception) {
                android.util.Log.e("DisputeUpload", "Upload exception", e)
                tvDisputeRecordingStatus.text = "Upload error: ${e.javaClass.simpleName} — retrying automatically"
                tvDisputeRecordingStatus.setTextColor(android.graphics.Color.parseColor("#F59E0B"))
                SurveySession.formData["dispute_recording_uri"] = videoFile.absolutePath
                val lat = (SurveySession.formData["capture_lat"] as? Double) ?: 0.0
                val lon = (SurveySession.formData["capture_lon"] as? Double) ?: 0.0
                QueueManager.enqueuePhoto(
                    requireContext(), surveyId, "dispute_recording", disputeLabel,
                    videoFile.absolutePath, com.cropsurvey.app.models.GpsCoords(lat, lon)
                )
            }
        }
    }

    private fun launchDisputeRecording() {
        val intent = Intent(requireContext(), com.cropsurvey.app.camera.VideoCaptureActivity::class.java)
        recordDisputeLauncher.launch(intent)
    }
    private val colorGreen get() = ContextCompat.getColor(requireContext(), R.color.primary)
    private val colorGrey = 0xFF64748B.toInt()

    // ─────────────────────────────────────────────────────────────────

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_cls_form, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupCollapsibleSections(view)
        setupSpinners()
        setupDatePickers()
        setupLiveValidation()
        loadStates()
        captureGps()
        restoreFormData()
        autoFillFarmerVerification()
        autoFillPolygonArea()
    }

    private fun autoFillPolygonArea() {
        val area = SurveySession.polygonAreaHectares
            ?: (SurveySession.formData["field_area_polygon"] as? Number)?.toDouble()
        etFieldAreaPolygon.setText(if (area != null) "%.4f".format(area) else "")
    }

    private fun bindViews(v: View) {
        etSurveyId           = v.findViewById(R.id.et_survey_id)
        spYear               = v.findViewById(R.id.sp_year)
        spSeason             = v.findViewById(R.id.sp_season)
        spScheme             = v.findViewById(R.id.sp_scheme)
        etOtherScheme        = v.findViewById(R.id.et_other_scheme)
        layoutOtherScheme    = v.findViewById(R.id.layout_other_scheme)
        spState              = v.findViewById(R.id.sp_state)
        spDistrict           = v.findViewById(R.id.sp_district)
        spTehsil             = v.findViewById(R.id.sp_tehsil)
        etRevenueCircle      = v.findViewById(R.id.et_revenue_circle)
        etGramPanchayat      = v.findViewById(R.id.et_gram_panchayat)
        etVillage            = v.findViewById(R.id.et_village)
        etSurveyIntimationNo = v.findViewById(R.id.et_survey_intimation_no)
        spCropName           = v.findViewById(R.id.sp_crop_name)
        spInsuranceUnit      = v.findViewById(R.id.sp_insurance_unit)
        etOtherCrop          = v.findViewById(R.id.et_other_crop)
        layoutOtherCrop      = v.findViewById(R.id.layout_other_crop)
        spAnyDisease         = v.findViewById(R.id.sp_any_disease)
        etDiseaseName        = v.findViewById(R.id.et_disease_name)
        layoutDiseaseName    = v.findViewById(R.id.layout_disease_name)
        etFarmerName         = v.findViewById(R.id.et_farmer_name)
        etFarmerMobile       = v.findViewById(R.id.et_farmer_mobile)
        etFarmerAppNo        = v.findViewById(R.id.et_farmer_app_no)
        spFarmerAvailable    = v.findViewById(R.id.sp_farmer_available)
        layoutFarmerUnavailable = v.findViewById(R.id.layout_farmer_unavailable)
        etRelationWithFarmer = v.findViewById(R.id.et_relation_with_farmer)
        etRepresentativeName = v.findViewById(R.id.et_representative_name)
        etRepresentativeMobile = v.findViewById(R.id.et_representative_mobile)
        etSurveyDate         = v.findViewById(R.id.et_survey_date)
        etSowingDate         = v.findViewById(R.id.et_sowing_date)
        etDateOfIntimation   = v.findViewById(R.id.et_date_of_intimation)
        etDateOfLoss         = v.findViewById(R.id.et_date_of_loss)
        spCauseOfEvent       = v.findViewById(R.id.sp_cause_of_event)
        etOtherCause         = v.findViewById(R.id.et_other_cause)
        layoutOtherCause     = v.findViewById(R.id.layout_other_cause)
        spCropStage          = v.findViewById(R.id.sp_crop_stage)
        spCroppingPattern    = v.findViewById(R.id.sp_cropping_pattern)
        spPostHarvest        = v.findViewById(R.id.sp_post_harvest)
        layoutPostHarvest    = v.findViewById(R.id.layout_post_harvest)
        etHarvestDate        = v.findViewById(R.id.et_harvest_date)
        spCropSituationField = v.findViewById(R.id.sp_crop_situation_field)
        etKhasraNo           = v.findViewById(R.id.et_khasra_no)
        etFieldAreaPolygon   = v.findViewById(R.id.et_field_area_polygon)
        etTotalLandArea      = v.findViewById(R.id.et_total_land_area)
        etInsuredArea        = v.findViewById(R.id.et_insured_area)
        etAreaAffectedPct    = v.findViewById(R.id.et_area_affected_pct)
        etLossPct            = v.findViewById(R.id.et_loss_pct)
        etRecoveryRate       = v.findViewById(R.id.et_recovery_rate)
        spOnfieldCondition   = v.findViewById(R.id.sp_onfield_condition)
        etOfficerName        = v.findViewById(R.id.et_officer_name)
        etOfficerMobile      = v.findViewById(R.id.et_officer_mobile)
        etOfficerDesignation = v.findViewById(R.id.et_officer_designation)
        spDisputeIfAny       = v.findViewById(R.id.sp_dispute_if_any)
        layoutDisputeRecording = v.findViewById(R.id.layout_dispute_recording)
        btnRecordDispute = v.findViewById(R.id.btn_record_dispute)
        tvDisputeRecordingStatus = v.findViewById(R.id.tv_dispute_recording_status)
        btnRecordDispute.setOnClickListener { launchDisputeRecording() }
        SurveySession.formData["dispute_recording_uri"]?.toString()?.takeIf { it.isNotEmpty() }?.let {
            tvDisputeRecordingStatus.text = "Recorded ✓"
            tvDisputeRecordingStatus.setTextColor(android.graphics.Color.parseColor("#16A34A"))
        }
        etRemarks            = v.findViewById(R.id.et_remarks)
        tvGpsCoords          = v.findViewById(R.id.tv_gps_coords)

        lblSurveyId          = v.findViewById(R.id.lbl_survey_id)
        lblYear              = v.findViewById(R.id.lbl_year)
        lblSeason            = v.findViewById(R.id.lbl_season)
        lblScheme            = v.findViewById(R.id.lbl_scheme)
        lblOtherScheme       = v.findViewById(R.id.lbl_other_scheme)
        lblState             = v.findViewById(R.id.lbl_state)
        lblDistrict          = v.findViewById(R.id.lbl_district)
        lblTehsil            = v.findViewById(R.id.lbl_tehsil)
        lblRevenueCircle     = v.findViewById(R.id.lbl_revenue_circle)
        lblGramPanchayat     = v.findViewById(R.id.lbl_gram_panchayat)
        lblVillage           = v.findViewById(R.id.lbl_village)
        lblSurveyIntimationNo = v.findViewById(R.id.lbl_survey_intimation_no)
        lblCropName          = v.findViewById(R.id.lbl_crop_name)
        lblInsuranceUnit     = v.findViewById(R.id.lbl_insurance_unit)
        lblOtherCrop         = v.findViewById(R.id.lbl_other_crop)
        lblAnyDisease        = v.findViewById(R.id.lbl_any_disease)
        lblDiseaseName       = v.findViewById(R.id.lbl_disease_name)
        lblFarmerName        = v.findViewById(R.id.lbl_farmer_name)
        lblFarmerMobile      = v.findViewById(R.id.lbl_farmer_mobile)
        lblFarmerAppNo       = v.findViewById(R.id.lbl_farmer_app_no)
        lblSurveyDate        = v.findViewById(R.id.lbl_survey_date)
        lblSowingDate        = v.findViewById(R.id.lbl_sowing_date)
        lblDateOfIntimation  = v.findViewById(R.id.lbl_date_of_intimation)
        lblDateOfLoss        = v.findViewById(R.id.lbl_date_of_loss)
        lblCauseOfEvent      = v.findViewById(R.id.lbl_cause_of_event)
        lblOtherCause        = v.findViewById(R.id.lbl_other_cause)
        lblCropStage         = v.findViewById(R.id.lbl_crop_stage)
        lblCroppingPattern   = v.findViewById(R.id.lbl_cropping_pattern)
        lblKhasraNo          = v.findViewById(R.id.lbl_khasra_no)
        lblFieldAreaPolygon  = v.findViewById(R.id.lbl_field_area_polygon)
        lblTotalLandArea     = v.findViewById(R.id.lbl_total_land_area)
        lblInsuredArea       = v.findViewById(R.id.lbl_insured_area)
        lblAreaAffectedPct   = v.findViewById(R.id.lbl_area_affected_pct)
        lblLossPct           = v.findViewById(R.id.lbl_loss_pct)
        lblRecoveryRate      = v.findViewById(R.id.lbl_recovery_rate)
        lblOnfieldCondition  = v.findViewById(R.id.lbl_onfield_condition)
        lblFarmerAvailable   = v.findViewById(R.id.lbl_farmer_available)
        lblRelationWithFarmer = v.findViewById(R.id.lbl_relation_with_farmer)
        lblRepresentativeName = v.findViewById(R.id.lbl_representative_name)
        lblRepresentativeMobile = v.findViewById(R.id.lbl_representative_mobile)
        lblPostHarvest       = v.findViewById(R.id.lbl_post_harvest)
        lblHarvestDate       = v.findViewById(R.id.lbl_harvest_date)
        lblCropSituationField = v.findViewById(R.id.lbl_crop_situation_field)
        lblDisputeIfAny      = v.findViewById(R.id.lbl_dispute_if_any)
        lblOfficerName       = v.findViewById(R.id.lbl_officer_name)
        lblOfficerMobile     = v.findViewById(R.id.lbl_officer_mobile)
        lblOfficerDesignation = v.findViewById(R.id.lbl_officer_designation)
        lblRemarks           = v.findViewById(R.id.lbl_remarks)
    }

    // ─────────────────────────────────────────────────────────────────
    // Field-level UI helpers
    // ─────────────────────────────────────────────────────────────────

    private fun updateFieldUi(field: EditText, label: TextView, frameId: Int) {
        val filled = field.text.isNotBlank()
        view?.findViewById<FrameLayout>(frameId)
            ?.setBackgroundResource(if (filled) R.drawable.input_bg_filled else R.drawable.input_background)
        label.setTextColor(if (filled) colorGreen else colorGrey)
    }

    private fun updateSpinnerUi(spinner: Spinner, label: TextView, frameId: Int) {
        val filled = spinner.selectedItemPosition > 0
        view?.findViewById<FrameLayout>(frameId)
            ?.setBackgroundResource(if (filled) R.drawable.input_bg_filled else R.drawable.input_background)
        label.setTextColor(if (filled) colorGreen else colorGrey)
    }

    private fun refreshFieldUi() {
        updateSpinnerUi(spYear,           lblYear,           R.id.frame_sp_year)
        updateFieldUi(etSurveyId,         lblSurveyId,       R.id.frame_et_survey_id)
        updateSpinnerUi(spSeason,         lblSeason,         R.id.frame_sp_season)
        updateSpinnerUi(spScheme,         lblScheme,         R.id.frame_sp_scheme)
        updateFieldUi(etOtherScheme,      lblOtherScheme,    R.id.frame_et_other_scheme)
        updateSpinnerUi(spState,          lblState,          R.id.frame_sp_state)
        updateSpinnerUi(spDistrict,       lblDistrict,       R.id.frame_sp_district)
        updateSpinnerUi(spTehsil,         lblTehsil,         R.id.frame_sp_tehsil)
        updateFieldUi(etRevenueCircle,    lblRevenueCircle,  R.id.frame_et_revenue_circle)
        updateFieldUi(etGramPanchayat,    lblGramPanchayat,  R.id.frame_et_gram_panchayat)
        updateFieldUi(etVillage,          lblVillage,        R.id.frame_et_village)
        updateFieldUi(etSurveyIntimationNo, lblSurveyIntimationNo, R.id.frame_et_survey_intimation_no)
        updateSpinnerUi(spCropName,       lblCropName,       R.id.frame_sp_crop_name)
        updateSpinnerUi(spInsuranceUnit,  lblInsuranceUnit,  R.id.frame_sp_insurance_unit)
        updateFieldUi(etOtherCrop,        lblOtherCrop,      R.id.frame_et_other_crop)
        updateSpinnerUi(spAnyDisease,     lblAnyDisease,     R.id.frame_sp_any_disease)
        updateFieldUi(etDiseaseName,      lblDiseaseName,    R.id.frame_et_disease_name)
        updateFieldUi(etFarmerName,       lblFarmerName,     R.id.frame_et_farmer_name)
        updateFieldUi(etFarmerMobile,     lblFarmerMobile,   R.id.frame_et_farmer_mobile)
        updateFieldUi(etFarmerAppNo,      lblFarmerAppNo,    R.id.frame_et_farmer_app_no)
        updateSpinnerUi(spFarmerAvailable, lblFarmerAvailable, R.id.frame_sp_farmer_available)
        updateFieldUi(etRelationWithFarmer, lblRelationWithFarmer, R.id.frame_et_relation_with_farmer)
        updateFieldUi(etRepresentativeName, lblRepresentativeName, R.id.frame_et_representative_name)
        updateFieldUi(etRepresentativeMobile, lblRepresentativeMobile, R.id.frame_et_representative_mobile)
        updateFieldUi(etSurveyDate,       lblSurveyDate,     R.id.frame_et_survey_date)
        updateFieldUi(etSowingDate,       lblSowingDate,     R.id.frame_et_sowing_date)
        updateFieldUi(etDateOfIntimation, lblDateOfIntimation, R.id.frame_et_date_of_intimation)
        updateFieldUi(etDateOfLoss,       lblDateOfLoss,     R.id.frame_et_date_of_loss)
        updateSpinnerUi(spCauseOfEvent,   lblCauseOfEvent,   R.id.frame_sp_cause_of_event)
        updateFieldUi(etOtherCause,       lblOtherCause,     R.id.frame_et_other_cause)
        updateSpinnerUi(spCropStage,      lblCropStage,      R.id.frame_sp_crop_stage)
        updateSpinnerUi(spCroppingPattern, lblCroppingPattern, R.id.frame_sp_cropping_pattern)
        updateSpinnerUi(spPostHarvest,    lblPostHarvest,    R.id.frame_sp_post_harvest)
        updateFieldUi(etHarvestDate,      lblHarvestDate,    R.id.frame_et_harvest_date)
        updateSpinnerUi(spCropSituationField, lblCropSituationField, R.id.frame_sp_crop_situation_field)
        updateFieldUi(etKhasraNo,         lblKhasraNo,       R.id.frame_et_khasra_no)
        updateFieldUi(etFieldAreaPolygon, lblFieldAreaPolygon, R.id.frame_et_field_area_polygon)
        updateFieldUi(etTotalLandArea,    lblTotalLandArea,  R.id.frame_et_total_land_area)
        updateFieldUi(etInsuredArea,      lblInsuredArea,    R.id.frame_et_insured_area)
        updateFieldUi(etAreaAffectedPct,  lblAreaAffectedPct, R.id.frame_et_area_affected_pct)
        updateFieldUi(etLossPct,          lblLossPct,        R.id.frame_et_loss_pct)
        updateFieldUi(etRecoveryRate,     lblRecoveryRate,   R.id.frame_et_recovery_rate)
        updateSpinnerUi(spOnfieldCondition, lblOnfieldCondition, R.id.frame_sp_onfield_condition)
        updateFieldUi(etOfficerName,      lblOfficerName,    R.id.frame_et_officer_name)
        updateFieldUi(etOfficerMobile,    lblOfficerMobile,  R.id.frame_et_officer_mobile)
        updateFieldUi(etOfficerDesignation, lblOfficerDesignation, R.id.frame_et_officer_designation)
        updateFieldUi(etRemarks,          lblRemarks,        R.id.frame_et_remarks)
        updateSpinnerUi(spDisputeIfAny,   lblDisputeIfAny,   R.id.frame_sp_dispute_if_any)
    }

    // ─────────────────────────────────────────────────────────────────
    // Collapsible sections
    // ─────────────────────────────────────────────────────────────────

    private fun setupCollapsibleSections(root: View) {
        for (i in 1..8) {
            val header = root.findViewById<View>(
                resources.getIdentifier("section_header_$i", "id", requireContext().packageName))
            val body = root.findViewById<View>(
                resources.getIdentifier("section_body_$i", "id", requireContext().packageName))
            val arrow = root.findViewById<TextView>(
                resources.getIdentifier("arrow_$i", "id", requireContext().packageName))
            val check = root.findViewById<ImageView>(
                resources.getIdentifier("ic_check_$i", "id", requireContext().packageName))

            sectionHeaders[i]  = header
            sectionBodies[i]   = body
            sectionArrows[i]   = arrow
            sectionChecks[i]   = check
            sectionExpanded[i] = false
            sectionTouched[i]  = false

            body.visibility = View.GONE
            arrow.text = "▼"
            check.visibility = View.GONE
            header.setBackgroundResource(R.drawable.section_header_collapsed)

            header.setOnClickListener {
                sectionTouched[i] = true
                toggleSection(i)
            }
        }
    }

    private fun toggleSection(section: Int) {
        val expanded = sectionExpanded[section] ?: false
        val body     = sectionBodies[section]   ?: return
        val arrow    = sectionArrows[section]   ?: return
        val header   = sectionHeaders[section]  ?: return

        if (!expanded) {
            // Advance guide and show next section tip when user opens a section
            activity?.let { act ->
                val currentStep = AiGuideOverlay.currentStep(act)
                when (section) {
                    0 -> {
                        // Opening Basic Info — advance from FORM_OPEN (5) to FORM_BASIC_INFO (6)
                        if (currentStep == AiGuideOverlay.Step.FORM_OPEN) AiGuideOverlay.advance(act)
                        AiGuideOverlay.show(act, AiGuideOverlay.Step.FORM_BASIC_INFO)
                    }
                    1 -> {
                        // Opening Location — advance from FORM_BASIC_INFO (6) to FORM_LOCATION (7)
                        if (currentStep == AiGuideOverlay.Step.FORM_BASIC_INFO) AiGuideOverlay.advance(act)
                        AiGuideOverlay.show(act, AiGuideOverlay.Step.FORM_LOCATION)
                    }
                    2 -> {
                        // Opening Crop Details — advance from FORM_LOCATION (7) to FORM_CROP_DETAILS (8)
                        if (currentStep == AiGuideOverlay.Step.FORM_LOCATION) AiGuideOverlay.advance(act)
                        AiGuideOverlay.show(act, AiGuideOverlay.Step.FORM_CROP_DETAILS)
                    }
                }
            }
        }

        if (expanded) {
            body.visibility = View.GONE
            arrow.text = "▼"
            sectionExpanded[section] = false
            val isDone = isSectionDone(section)
            header.setBackgroundResource(when {
                isDone -> R.drawable.section_header_done_collapsed
                nextAttempted -> R.drawable.section_header_error_collapsed
                else -> R.drawable.section_header_collapsed
            })
        } else {
            body.visibility = View.VISIBLE
            arrow.text = "▲"
            sectionExpanded[section] = true
            val isDone = isSectionDone(section)
            header.setBackgroundResource(when {
                isDone -> R.drawable.section_header_done
                nextAttempted -> R.drawable.section_header_error
                else -> R.drawable.section_header_default
            })
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Live validation
    // ─────────────────────────────────────────────────────────────────

    private fun setupLiveValidation() {
        fun watcher(sectionHint: Int) = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                sectionTouched[sectionHint] = true
                refreshFieldUi()
                refreshSectionStatus()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        etVillage.addTextChangedListener(watcher(2))
        etRevenueCircle.addTextChangedListener(watcher(2))
        etGramPanchayat.addTextChangedListener(watcher(2))
        etSurveyIntimationNo.addTextChangedListener(watcher(3))
        etOtherCrop.addTextChangedListener(watcher(3))
        etDiseaseName.addTextChangedListener(watcher(3))
        etOtherCause.addTextChangedListener(watcher(6))
        etFarmerName.addTextChangedListener(watcher(4))
        etFarmerMobile.addTextChangedListener(watcher(4))
        etFarmerAppNo.addTextChangedListener(watcher(4))
        etRelationWithFarmer.addTextChangedListener(watcher(4))
        etRepresentativeName.addTextChangedListener(watcher(4))
        etRepresentativeMobile.addTextChangedListener(watcher(4))
        etSurveyDate.addTextChangedListener(watcher(5))
        etSowingDate.addTextChangedListener(watcher(5))
        etDateOfIntimation.addTextChangedListener(watcher(5))
        etDateOfLoss.addTextChangedListener(watcher(5))
        etKhasraNo.addTextChangedListener(watcher(6))
        etTotalLandArea.addTextChangedListener(watcher(6))
        etInsuredArea.addTextChangedListener(watcher(6))
        etHarvestDate.addTextChangedListener(watcher(6))
        etAreaAffectedPct.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                sectionTouched[6] = true
                validateAreaAffected()
                refreshFieldUi()
                refreshSectionStatus()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        etLossPct.addTextChangedListener(watcher(6))
        etRecoveryRate.addTextChangedListener(watcher(6))
        etOfficerName.addTextChangedListener(watcher(7))
        etOfficerMobile.addTextChangedListener(watcher(7))
        etOfficerDesignation.addTextChangedListener(watcher(7))
        etRemarks.addTextChangedListener(watcher(8))

        fun spinnerListener(sectionHint: Int, extra: AdapterView.OnItemSelectedListener? = null) =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    extra?.onItemSelected(p, v, pos, id)
                    sectionTouched[sectionHint] = true
                    refreshFieldUi()
                    refreshSectionStatus()
                }
                override fun onNothingSelected(p: AdapterView<*>?) { extra?.onNothingSelected(p) }
            }

        spYear.onItemSelectedListener   = spinnerListener(1)
        spSeason.onItemSelectedListener = spinnerListener(1)
        spScheme.onItemSelectedListener = spinnerListener(1, object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                layoutOtherScheme.visibility =
                    if (tdSchemes.getOrNull(pos - 1)?.code == "Others") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        })
        spCropName.onItemSelectedListener = spinnerListener(3, object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                layoutOtherCrop.visibility =
                    if (tdCrops.getOrNull(pos - 1)?.code in listOf("Others", "Other")) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        })
        spInsuranceUnit.onItemSelectedListener = spinnerListener(3)
        spAnyDisease.onItemSelectedListener = spinnerListener(3, object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                layoutDiseaseName.visibility =
                    if (tdYesNo.getOrNull(pos - 1)?.code == "yes") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        })
        spFarmerAvailable.onItemSelectedListener = spinnerListener(4, object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val code = tdYesNo.getOrNull(pos - 1)?.code
                layoutFarmerUnavailable.visibility = if (code == "no") View.VISIBLE else View.GONE
                // Update immediately so the Photos tab can show/hide the 14th
                // "Farmer Representative ID" photo requirement without waiting for a save.
                if (code != null) SurveySession.formData["farmer_available"] = code
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        })
        spCauseOfEvent.onItemSelectedListener = spinnerListener(6, object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                layoutOtherCause.visibility =
                    if (tdCauseEvent.getOrNull(pos - 1)?.code in listOf("Other", "Others")) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        })
        spCropStage.onItemSelectedListener     = spinnerListener(6)
        spCroppingPattern.onItemSelectedListener = spinnerListener(6)
        spPostHarvest.onItemSelectedListener = spinnerListener(6, object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                layoutPostHarvest.visibility =
                    if (tdYesNo.getOrNull(pos - 1)?.code == "yes") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        })
        spCropSituationField.onItemSelectedListener = spinnerListener(6)
        spOnfieldCondition.onItemSelectedListener = spinnerListener(6)
        spDisputeIfAny.onItemSelectedListener = spinnerListener(8, object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                layoutDisputeRecording.visibility =
                    if (tdYesNo.getOrNull(pos - 1)?.code == "yes") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        })
    }

    /** Called by SurveyTabsActivity once the short Case ID is fetched from the server. */
    fun refreshSurveyId(caseId: String) {
        if (::etSurveyId.isInitialized) {
            etSurveyId.setText(caseId)
            refreshFieldUi()
        }
    }

    private fun validateAreaAffected() {
        val a = etAreaAffectedPct.text.toString().toDoubleOrNull()
        etAreaAffectedPct.error =
            if (a != null && (a < 0 || a > 100)) "Area affected must be between 0 and 100%" else null
    }

    // ─────────────────────────────────────────────────────────────────
    // Section completion logic
    // ─────────────────────────────────────────────────────────────────

    private fun isSectionDone(section: Int): Boolean = when (section) {
        // GREEN = ALL fields in section filled (required AND optional)
        // S1: year, season, scheme, other_scheme(conditional)
        1 -> spYear.selectedItemPosition > 0
                && spSeason.selectedItemPosition > 0
                && spScheme.selectedItemPosition > 0
                && (tdSchemes.getOrNull(spScheme.selectedItemPosition - 1)?.code != "Others" || etOtherScheme.text.isNotBlank())
        // S2: state, district, tehsil, revenue_circle, gram_panchayat, village
        2 -> spState.selectedItemPosition > 0
                && spDistrict.selectedItemPosition > 0
                && spTehsil.selectedItemPosition > 0
                && etRevenueCircle.text.isNotBlank()
                && etGramPanchayat.text.isNotBlank()
                && etVillage.text.isNotBlank()
        // S3: survey_intimation_no, crop_name, insurance_unit, other_crop(conditional), any_disease, disease_name(conditional)
        3 -> etSurveyIntimationNo.text.isNotBlank()
                && spCropName.selectedItemPosition > 0
                && spInsuranceUnit.selectedItemPosition > 0
                && (tdCrops.getOrNull(spCropName.selectedItemPosition - 1)?.code !in listOf("Others", "Other") || etOtherCrop.text.isNotBlank())
                && spAnyDisease.selectedItemPosition > 0
                && (tdYesNo.getOrNull(spAnyDisease.selectedItemPosition - 1)?.code != "yes" || etDiseaseName.text.isNotBlank())
        // S4: farmer_name, farmer_mobile, farmer_app_no, farmer_available (+ representative block if No)
        4 -> etFarmerName.text.isNotBlank()
                && etFarmerMobile.text.length >= 10
                && etFarmerAppNo.text.isNotBlank()
                && spFarmerAvailable.selectedItemPosition > 0
                && (tdYesNo.getOrNull(spFarmerAvailable.selectedItemPosition - 1)?.code != "no"
                        || (etRelationWithFarmer.text.isNotBlank()
                            && etRepresentativeName.text.isNotBlank()
                            && etRepresentativeMobile.text.length >= 10))
        // S5: survey_date, sowing_date, date_of_intimation, date_of_loss
        5 -> etSurveyDate.text.isNotBlank()
                && etSowingDate.text.isNotBlank()
                && etDateOfIntimation.text.isNotBlank()
                && etDateOfLoss.text.isNotBlank()
        // S6: cause_of_event, crop_stage, cropping_pattern, post_harvest (+harvest fields), khasra_no, field_area, total_land, insured_area, area_affected, loss_pct, recovery_rate, onfield_condition
        6 -> spCauseOfEvent.selectedItemPosition > 0
                && (tdCauseEvent.getOrNull(spCauseOfEvent.selectedItemPosition - 1)?.code !in listOf("Other", "Others") || etOtherCause.text.isNotBlank())
                && spCropStage.selectedItemPosition > 0
                && spCroppingPattern.selectedItemPosition > 0
                && spPostHarvest.selectedItemPosition > 0
                && (tdYesNo.getOrNull(spPostHarvest.selectedItemPosition - 1)?.code != "yes"
                        || (etHarvestDate.text.isNotBlank() && spCropSituationField.selectedItemPosition > 0))
                && etKhasraNo.text.isNotBlank()
                && etFieldAreaPolygon.text.isNotBlank()
                && etTotalLandArea.text.isNotBlank()
                && etInsuredArea.text.isNotBlank()
                && etAreaAffectedPct.text.isNotBlank()
                && etLossPct.text.isNotBlank()
                && etRecoveryRate.text.isNotBlank()
                && spOnfieldCondition.selectedItemPosition > 0
        // S7: officer_name, officer_mobile, officer_designation
        7 -> etOfficerName.text.isNotBlank()
                && etOfficerMobile.text.length >= 10
                && etOfficerDesignation.text.isNotBlank()
        // S8: dispute_if_any, remarks
        8 -> spDisputeIfAny.selectedItemPosition > 0 && etRemarks.text.isNotBlank()
        else -> false
    }
    fun refreshSectionStatus() {
        for (i in 1..8) {
            updateSectionUi(i, isSectionDone(i))
        }
    }

    private fun updateSectionUi(section: Int, isDone: Boolean) {
        val header   = sectionHeaders[section] ?: return
        val check    = sectionChecks[section]  ?: return
        val expanded = sectionExpanded[section] ?: false

        when {
            isDone -> {
                header.setBackgroundResource(
                    if (expanded) R.drawable.section_header_done else R.drawable.section_header_done_collapsed)
                check.visibility = View.VISIBLE
                check.setBackgroundResource(R.drawable.circle_green)
                check.setImageResource(R.drawable.ic_check_white)
            }
            nextAttempted -> {
                header.setBackgroundResource(
                    if (expanded) R.drawable.section_header_error else R.drawable.section_header_error_collapsed)
                check.visibility = View.VISIBLE
                check.setBackgroundResource(R.drawable.circle_red)
                check.setImageResource(R.drawable.ic_close_white)
            }
            else -> {
                header.setBackgroundResource(
                    if (expanded) R.drawable.section_header_default else R.drawable.section_header_collapsed)
                check.visibility = View.GONE
            }
        }
    }

    /**
     * Called by the host Activity when the user taps NEXT.
     * Returns false if any required section is incomplete.
     */
    fun markAllTouchedAndValidate(): Boolean {
        nextAttempted = true
        refreshSectionStatus()
        val allDone = (1..8).all { isSectionDone(it) }
        if (!allDone) {
            val firstIncomplete = (1..8).firstOrNull { !isSectionDone(it) }
            if (firstIncomplete != null && sectionExpanded[firstIncomplete] == false) {
                toggleSection(firstIncomplete)
            }
        }
        return allDone
    }

    // ─────────────────────────────────────────────────────────────────

    private fun setSpinner(spinner: Spinner, items: List<String>) {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    // ── TranslatedDropdown helpers (inline) ───────────────────────────────────
    private fun tdLabels(opts: List<TranslatedDropdown.Option>): List<String> = opts.map { it.label }
    private fun tdCode(opts: List<TranslatedDropdown.Option>, pos: Int): String? = opts.getOrNull(pos)?.code
    private fun tdPosition(opts: List<TranslatedDropdown.Option>, code: String?): Int = opts.indexOfFirst { it.code == code }
    private fun tdRestore(sp: android.widget.Spinner, opts: List<TranslatedDropdown.Option>, code: String?) {
        if (code == null) return
        val p = tdPosition(opts, code)
        if (p >= 0) sp.setSelection(p + 1, false)
    }

    private fun restoreSpinner(spinner: Spinner, value: String?) {
        if (value == null) return
        val adapter = spinner.adapter ?: return
        for (i in 0 until adapter.count) {
            if (adapter.getItem(i)?.toString() == value) {
                spinner.setSelection(i, false)
                return
            }
        }
    }

    private var tdSeasons     = listOf<TranslatedDropdown.Option>()
    private var tdSchemes     = listOf<TranslatedDropdown.Option>()
    private var tdCrops       = listOf<TranslatedDropdown.Option>()
    private var tdInsurance   = listOf<TranslatedDropdown.Option>()
    private var tdCropStages  = listOf<TranslatedDropdown.Option>()
    private var tdCauseEvent  = listOf<TranslatedDropdown.Option>()
    private var tdCropPattern = listOf<TranslatedDropdown.Option>()
    private var tdOnfield     = listOf<TranslatedDropdown.Option>()
    private var tdYesNo       = listOf<TranslatedDropdown.Option>()
    private var tdCropSituationField = listOf<TranslatedDropdown.Option>()

    private fun setupSpinners() {
        val ctx = requireContext()
        tdSeasons     = TranslatedDropdown.seasons(ctx)
        tdSchemes     = TranslatedDropdown.schemes(ctx)
        tdCrops       = TranslatedDropdown.crops(ctx)
        tdInsurance   = TranslatedDropdown.insuranceUnits(ctx)
        tdCropStages  = TranslatedDropdown.cropStages(ctx)
        tdCauseEvent  = TranslatedDropdown.causeOfEvent(ctx)
        tdCropPattern = TranslatedDropdown.croppingPatterns(ctx)
        tdOnfield     = TranslatedDropdown.onfieldConditions(ctx)
        tdYesNo       = TranslatedDropdown.yesNo(ctx)
        tdCropSituationField = TranslatedDropdown.cropSituationField(ctx)

        setSpinner(spYear,            listOf("Select Year")       + AppConfig.YEARS)
        setSpinner(spSeason,          listOf(getString(R.string.hint_select)) + tdLabels(tdSeasons))
        setSpinner(spCropName,        listOf(getString(R.string.hint_select)) + tdLabels(tdCrops))
        setSpinner(spInsuranceUnit,   listOf(getString(R.string.hint_select)) + tdLabels(tdInsurance))
        setSpinner(spCauseOfEvent,    listOf(getString(R.string.hint_select)) + tdLabels(tdCauseEvent))
        setSpinner(spCropStage,       listOf(getString(R.string.hint_select)) + tdLabels(tdCropStages))
        setSpinner(spCroppingPattern, listOf(getString(R.string.hint_select)) + tdLabels(tdCropPattern))
        setSpinner(spOnfieldCondition,listOf(getString(R.string.hint_select)) + tdLabels(tdOnfield))
        setSpinner(spScheme,          listOf(getString(R.string.hint_select)) + tdLabels(tdSchemes))
        setSpinner(spFarmerAvailable, listOf(getString(R.string.hint_select)) + tdLabels(tdYesNo))
        setSpinner(spPostHarvest,     listOf(getString(R.string.hint_select)) + tdLabels(tdYesNo))
        setSpinner(spCropSituationField, listOf(getString(R.string.hint_select)) + tdLabels(tdCropSituationField))
        setSpinner(spDisputeIfAny,    listOf(getString(R.string.hint_select)) + tdLabels(tdYesNo))
        setSpinner(spAnyDisease,      listOf(getString(R.string.hint_select)) + tdLabels(tdYesNo))
        setSpinner(spState,    listOf("Select State"))
        setSpinner(spDistrict, listOf("Select District"))
        setSpinner(spTehsil,   listOf("Select Tehsil"))

        spState.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (isRestoring) return
                val state = states.getOrNull(pos - 1) ?: return
                SurveySession.formData["state"] = state
                sectionTouched[2] = true
                loadDistricts(state)
                refreshFieldUi(); refreshSectionStatus()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        spDistrict.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (isRestoring) return
                val district = districts.getOrNull(pos - 1) ?: return
                SurveySession.formData["district"] = district
                val state = SurveySession.formData["state"]?.toString() ?: return
                sectionTouched[2] = true
                loadSubDistricts(state, district)
                refreshFieldUi(); refreshSectionStatus()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        spTehsil.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (isRestoring) return
                val tehsil = subDistricts.getOrNull(pos - 1) ?: return
                SurveySession.formData["tehsil"] = tehsil
                sectionTouched[2] = true
                refreshFieldUi(); refreshSectionStatus()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun setupDatePickers() {
        listOf(etSowingDate, etDateOfIntimation, etDateOfLoss, etHarvestDate).forEach { et ->
            et.isFocusable = false
            et.setOnClickListener {
                val cal = Calendar.getInstance()
                val dialog = DatePickerDialog(requireContext(), { _, y, m, d ->
                    et.setText(java.lang.String.format(java.util.Locale.US, "%04d-%02d-%02d", y, m + 1, d))
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                dialog.datePicker.maxDate = System.currentTimeMillis()
                dialog.show()
            }
        }

        // Survey Date = today, read-only, auto-filled
        val cal = Calendar.getInstance()
        etSurveyDate.setText(java.lang.String.format(
            java.util.Locale.US, "%04d-%02d-%02d",
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)))
        etSurveyDate.isFocusable = false
        etSurveyDate.isClickable = false
    }

    private fun loadStates() {
        lifecycleScope.launch {
            try {
                val res = ApiClient.service.getStates()
                if (res.isSuccessful) {
                    states = res.body() ?: emptyList()
                    setSpinner(spState, listOf("Select State") + states)
                    restoreSpinner(spState, SurveySession.formData["state"]?.toString())
                }
            } catch (e: Exception) {}
        }
    }

    private fun loadDistricts(state: String) {
        lifecycleScope.launch {
            try {
                val res = ApiClient.service.getDistricts(state)
                if (res.isSuccessful) {
                    districts = res.body() ?: emptyList()
                    setSpinner(spDistrict, listOf("Select District") + districts)
                }
            } catch (e: Exception) {}
        }
    }

    private fun loadSubDistricts(state: String, district: String) {
        lifecycleScope.launch {
            try {
                val res = ApiClient.service.getSubDistricts(state, district)
                if (res.isSuccessful) {
                    subDistricts = res.body() ?: emptyList()
                    setSpinner(spTehsil, listOf("Select Tehsil") + subDistricts)
                }
            } catch (e: Exception) {}
        }
    }

    private fun captureGps() {
        lifecycleScope.launch {
            val coords = GpsHelper.getCurrentLocation(requireContext())
            if (coords != null) {
                tvGpsCoords.text = GpsHelper.formatCoords(coords)
                SurveySession.formData["capture_lat"] = coords.lat
                SurveySession.formData["capture_lon"] = coords.lon
            } else {
                tvGpsCoords.text = "Acquiring GPS..."
            }
        }
    }

    private fun restoreFormData() {
        // Prefer the short display Case ID (e.g. "CK0J9MGTHU") fetched from the
        // server — falls back to whatever's cached, then the raw UUID as a
        // last resort until fetchCaseId() in SurveyTabsActivity resolves it.
        val surveyId = SurveySession.currentCaseId.takeIf { it.isNotEmpty() }
            ?: SurveySession.formData["survey_id"]?.toString()
            ?: activity?.intent?.getStringExtra("survey_id")
        etSurveyId.setText(surveyId ?: "")

        val fd = SurveySession.formData
        if (fd.isEmpty()) return

        etFarmerName.setText(fd["farmer_name"]?.toString() ?: "")
        etFarmerMobile.setText(fd["farmer_mobile"]?.toString() ?: "")
        etFarmerAppNo.setText(fd["farmer_insurance_app_no"]?.toString() ?: "")
        etRelationWithFarmer.setText(fd["relation_with_farmer"]?.toString() ?: "")
        etRepresentativeName.setText(fd["representative_name"]?.toString() ?: "")
        etRepresentativeMobile.setText(fd["representative_mobile"]?.toString() ?: "")
        etVillage.setText(fd["village"]?.toString() ?: "")
        etRevenueCircle.setText(fd["revenue_circle"]?.toString() ?: "")
        etGramPanchayat.setText(fd["gram_panchayat"]?.toString() ?: "")
        etSurveyIntimationNo.setText(fd["survey_intimation_no"]?.toString() ?: "")
        etKhasraNo.setText(fd["khasra_no"]?.toString() ?: "")
        etTotalLandArea.setText(fd["total_land_area"]?.toString() ?: "")
        etInsuredArea.setText(fd["insured_area"]?.toString() ?: "")
        etAreaAffectedPct.setText(fd["area_affected_pct"]?.toString() ?: "")
        etLossPct.setText(fd["loss_pct"]?.toString() ?: "")
        etRecoveryRate.setText(fd["recovery_rate_pct"]?.toString() ?: "")
        etHarvestDate.setText(fd["harvest_date"]?.toString() ?: "")
        etOfficerName.setText(fd["govt_officer_name"]?.toString() ?: "")
        etOfficerMobile.setText(fd["govt_officer_mobile"]?.toString() ?: "")
        etOfficerDesignation.setText(fd["govt_officer_designation"]?.toString() ?: "")
        etRemarks.setText(fd["remarks"]?.toString() ?: "")
        etOtherScheme.setText(fd["others_scheme"]?.toString() ?: "")
        etOtherCrop.setText(fd["other_crop"]?.toString() ?: "")
        etDiseaseName.setText(fd["disease_name"]?.toString() ?: "")
        etSowingDate.setText(fd["sowing_date"]?.toString() ?: "")
        etDateOfIntimation.setText(fd["date_of_intimation"]?.toString() ?: "")
        etDateOfLoss.setText(fd["date_of_loss"]?.toString() ?: "")
        // Survey date is auto-filled with today's date by setupDatePickers(); only override on edit/resubmit
        fd["survey_date"]?.toString()?.takeIf { it.isNotEmpty() }?.let { etSurveyDate.setText(it) }

        restoreSpinner(spYear,            fd["year"]?.toString())
        tdRestore(spSeason, tdSeasons, fd["season"]?.toString())
        tdRestore(spScheme, tdSchemes, fd["scheme"]?.toString())
        tdRestore(spCropName, tdCrops, fd["crop_name"]?.toString())
        tdRestore(spInsuranceUnit, tdInsurance, fd["insurance_unit"]?.toString())
        tdRestore(spCauseOfEvent, tdCauseEvent, fd["cause_of_event"]?.toString())
        etOtherCause.setText(fd["other_cause"]?.toString() ?: "")
        tdRestore(spCropStage, tdCropStages, fd["crop_stage"]?.toString())
        tdRestore(spCroppingPattern, tdCropPattern, fd["cropping_pattern"]?.toString())
        tdRestore(spOnfieldCondition, tdOnfield, fd["onfield_condition"]?.toString())
        tdRestore(spFarmerAvailable, tdYesNo, fd["farmer_available"]?.toString())
        fd["farmer_available"]?.toString()?.let { SurveySession.formData["farmer_available"] = it }
        tdRestore(spPostHarvest, tdYesNo, fd["post_harvest"]?.toString())
        tdRestore(spCropSituationField, tdCropSituationField, fd["crop_situation_field"]?.toString())
        tdRestore(spDisputeIfAny, tdYesNo, fd["dispute_if_any"]?.toString())
        tdRestore(spAnyDisease, tdYesNo, fd["any_disease"]?.toString())

        // Apply conditional visibility based on restored values
        layoutOtherScheme.visibility = if (fd["scheme"]?.toString() == "Others") View.VISIBLE else View.GONE
        layoutOtherCrop.visibility = if (fd["crop_name"]?.toString() in listOf("Others", "Other")) View.VISIBLE else View.GONE
        layoutDiseaseName.visibility = if (fd["any_disease"]?.toString() == "yes") View.VISIBLE else View.GONE
        layoutOtherCause.visibility = if (fd["cause_of_event"]?.toString() in listOf("Other", "Others")) View.VISIBLE else View.GONE
        layoutFarmerUnavailable.visibility = if (fd["farmer_available"]?.toString() == "no") View.VISIBLE else View.GONE
        layoutPostHarvest.visibility = if (fd["post_harvest"]?.toString() == "yes") View.VISIBLE else View.GONE
        layoutDisputeRecording.visibility = if (fd["dispute_if_any"]?.toString() == "yes") View.VISIBLE else View.GONE

        // Only expand on edit/resubmit — new surveys start collapsed
        val isEditRestore = activity?.intent?.getBooleanExtra("is_edit_mode", false) == true ||
                activity?.intent?.getBooleanExtra("is_resubmit", false) == true
        for (i in 1..8) {
            // Only mark touched on edit/resubmit — on new survey sections start untouched (no tick)
            if (isEditRestore) {
                sectionTouched[i] = true
                sectionExpanded[i] = true
                sectionBodies[i]?.visibility = android.view.View.VISIBLE
                sectionArrows[i]?.text = "▲"
            }
        }
        refreshFieldUi()
        refreshSectionStatus()

        val savedState    = fd["state"]?.toString()    ?: return
        val savedDistrict = fd["district"]?.toString() ?: return
        val savedTehsil   = fd["tehsil"]?.toString()

        lifecycleScope.launch {
            isRestoring = true
            try {
                val resD = ApiClient.service.getDistricts(savedState)
                if (resD.isSuccessful) {
                    districts = resD.body() ?: emptyList()
                    setSpinner(spDistrict, listOf("Select District") + districts)
                    restoreSpinner(spDistrict, savedDistrict)
                    SurveySession.formData["district"] = savedDistrict
                    if (savedTehsil != null) {
                        val resT = ApiClient.service.getSubDistricts(savedState, savedDistrict)
                        if (resT.isSuccessful) {
                            subDistricts = resT.body() ?: emptyList()
                            setSpinner(spTehsil, listOf("Select Tehsil") + subDistricts)
                            restoreSpinner(spTehsil, savedTehsil)
                            SurveySession.formData["tehsil"] = savedTehsil
                        }
                    }
                }
            } catch (e: Exception) {}
            finally {
                isRestoring = false
                refreshFieldUi()
                refreshSectionStatus()
            }
        }
    }

    /** Check only * required fields. Scroll to first missing. */
    fun validateRequiredFields(): Boolean {
        if (spYear.selectedItemPosition == 0) { expandAndScrollTo(1, spYear); return false }
        if (spSeason.selectedItemPosition == 0) { expandAndScrollTo(1, spSeason); return false }
        if (spState.selectedItemPosition == 0) { expandAndScrollTo(2, spState); return false }
        if (spDistrict.selectedItemPosition == 0) { expandAndScrollTo(2, spDistrict); return false }
        if (etVillage.text.isBlank()) { etVillage.error = "Required"; expandAndScrollTo(2, etVillage); return false }
        if (spCropName.selectedItemPosition == 0) { expandAndScrollTo(3, spCropName); return false }
        if (etFarmerName.text.isBlank()) { etFarmerName.error = "Required"; expandAndScrollTo(4, etFarmerName); return false }
        if (etFarmerMobile.text.length < 10) { etFarmerMobile.error = "Valid 10-digit mobile required"; expandAndScrollTo(4, etFarmerMobile); return false }
        if (etTotalLandArea.text.isBlank()) { etTotalLandArea.error = "Required"; expandAndScrollTo(6, etTotalLandArea); return false }
        return true
    }

    private fun expandAndScrollTo(section: Int, target: android.view.View) {
        if (sectionExpanded[section] != true) {
            sectionBodies[section]?.visibility = android.view.View.VISIBLE
            sectionExpanded[section] = true
            sectionArrows[section]?.text = "▲"
            refreshSectionStatus()
        }
        val sv = requireActivity().findViewById<android.widget.ScrollView>(R.id.form_scroll_view)
        sv?.post { sv.smoothScrollTo(0, (target.top - 100).coerceAtLeast(0)) }
        target.requestFocus()
        android.widget.Toast.makeText(requireContext(), "Please fill all required fields (marked *)", android.widget.Toast.LENGTH_SHORT).show()
    }

    /** Collects all current field values WITHOUT validation — used for draft auto-save. */
    fun collectDraftData(): Map<String, Any?> {
        val farmerName    = etFarmerName.text.toString().trim()
        val farmerMobile  = etFarmerMobile.text.toString().trim()
        val village       = etVillage.text.toString().trim()
        val totalLandArea = etTotalLandArea.text.toString().trim()
        val insuredArea   = etInsuredArea.text.toString().trim()

        return mapOf(
            "survey_id"                to (etSurveyId.text.toString().takeIf { it.isNotEmpty() } ?: SurveySession.formData["survey_id"]),
            "year"                     to spYear.selectedItem?.toString()?.takeIf { it != "Select Year" },
            "season"                   to tdCode(tdSeasons, spSeason.selectedItemPosition - 1),
            "scheme"                   to tdCode(tdSchemes, spScheme.selectedItemPosition - 1),
            "others_scheme"            to etOtherScheme.text.toString().takeIf { it.isNotEmpty() },
            "state"                    to SurveySession.formData["state"],
            "district"                 to SurveySession.formData["district"],
            "tehsil"                   to (spTehsil.selectedItem?.toString()?.takeIf { it != "Select Tehsil" } ?: SurveySession.formData["tehsil"]),
            "revenue_circle"           to etRevenueCircle.text.toString().takeIf { it.isNotEmpty() },
            "gram_panchayat"           to etGramPanchayat.text.toString().takeIf { it.isNotEmpty() },
            "village"                  to village.takeIf { it.isNotEmpty() },
            "survey_intimation_no"     to etSurveyIntimationNo.text.toString().takeIf { it.isNotEmpty() },
            "crop_name"                to tdCode(tdCrops, spCropName.selectedItemPosition - 1),
            "insurance_unit"           to tdCode(tdInsurance, spInsuranceUnit.selectedItemPosition - 1),
            "other_crop"               to etOtherCrop.text.toString().takeIf { it.isNotEmpty() },
            "any_disease"              to tdCode(tdYesNo, spAnyDisease.selectedItemPosition - 1),
            "disease_name"             to etDiseaseName.text.toString().takeIf { it.isNotEmpty() },
            "farmer_name"              to farmerName.takeIf { it.isNotEmpty() },
            "farmer_mobile"            to farmerMobile.takeIf { it.isNotEmpty() },
            "farmer_insurance_app_no"  to etFarmerAppNo.text.toString().takeIf { it.isNotEmpty() },
            "farmer_verified"          to (SurveySession.formData["farmer_verified"]?.toString() ?: "skipped"),
            "farmer_phone_verified"    to (SurveySession.formData["farmer_phone"]?.toString() ?: ""),
            "farmer_available"         to tdCode(tdYesNo, spFarmerAvailable.selectedItemPosition - 1),
            "relation_with_farmer"     to etRelationWithFarmer.text.toString().takeIf { it.isNotEmpty() },
            "representative_name"      to etRepresentativeName.text.toString().takeIf { it.isNotEmpty() },
            "representative_mobile"    to etRepresentativeMobile.text.toString().takeIf { it.isNotEmpty() },
            "survey_date"              to etSurveyDate.text.toString().takeIf { it.isNotEmpty() },
            "sowing_date"              to etSowingDate.text.toString().takeIf { it.isNotEmpty() },
            "date_of_intimation"       to etDateOfIntimation.text.toString().takeIf { it.isNotEmpty() },
            "date_of_loss"             to etDateOfLoss.text.toString().takeIf { it.isNotEmpty() },
            "cause_of_event"           to tdCode(tdCauseEvent, spCauseOfEvent.selectedItemPosition - 1),
            "other_cause"              to etOtherCause.text.toString().takeIf { it.isNotEmpty() },
            "crop_stage"               to tdCode(tdCropStages, spCropStage.selectedItemPosition - 1),
            "cropping_pattern"         to tdCode(tdCropPattern, spCroppingPattern.selectedItemPosition - 1),
            "post_harvest"             to tdCode(tdYesNo, spPostHarvest.selectedItemPosition - 1),
            "harvest_date"             to etHarvestDate.text.toString().takeIf { it.isNotEmpty() },
            "crop_situation_field"     to tdCode(tdCropSituationField, spCropSituationField.selectedItemPosition - 1),
            "khasra_no"                to etKhasraNo.text.toString().takeIf { it.isNotEmpty() },
            "total_land_area"          to totalLandArea.toDoubleOrNull(),
            "insured_area"             to insuredArea.toDoubleOrNull(),
            "area_affected_pct"        to etAreaAffectedPct.text.toString().toDoubleOrNull(),
            "loss_pct"                 to etLossPct.text.toString().toDoubleOrNull(),
            "recovery_rate_pct"        to etRecoveryRate.text.toString().toDoubleOrNull(),
            "onfield_condition"        to tdCode(tdOnfield, spOnfieldCondition.selectedItemPosition - 1),
            "govt_officer_name"        to etOfficerName.text.toString().takeIf { it.isNotEmpty() },
            "govt_officer_mobile"      to etOfficerMobile.text.toString().takeIf { it.isNotEmpty() },
            "govt_officer_designation" to etOfficerDesignation.text.toString().takeIf { it.isNotEmpty() },
            "dispute_if_any"           to tdCode(tdYesNo, spDisputeIfAny.selectedItemPosition - 1),
            "dispute_recording_uri"    to SurveySession.formData["dispute_recording_uri"],
        )
    }

    fun collectFormData(): Map<String, Any?> {
        val farmerName    = etFarmerName.text.toString().trim()
        val farmerMobile  = etFarmerMobile.text.toString().trim()
        val village       = etVillage.text.toString().trim()
        val totalLandArea = etTotalLandArea.text.toString().trim()
        val insuredArea   = etInsuredArea.text.toString().trim()

        if (farmerName.isEmpty())     { etFarmerName.error = "Required"; return emptyMap() }
        if (farmerMobile.length < 10) { etFarmerMobile.error = "Enter valid mobile"; return emptyMap() }
        if (village.isEmpty())        { etVillage.error = "Required"; return emptyMap() }

        return mapOf(
            "survey_id"                to (etSurveyId.text.toString().takeIf { it.isNotEmpty() } ?: SurveySession.formData["survey_id"]),
            "year"                     to spYear.selectedItem?.toString()?.takeIf { it != "Select Year" },
            "season"                   to tdCode(tdSeasons, spSeason.selectedItemPosition - 1),
            "scheme"                   to tdCode(tdSchemes, spScheme.selectedItemPosition - 1),
            "others_scheme"            to etOtherScheme.text.toString().takeIf { it.isNotEmpty() },
            "state"                    to SurveySession.formData["state"],
            "district"                 to SurveySession.formData["district"],
            "tehsil"                   to (spTehsil.selectedItem?.toString()?.takeIf { it != "Select Tehsil" } ?: SurveySession.formData["tehsil"]),
            "revenue_circle"           to etRevenueCircle.text.toString().takeIf { it.isNotEmpty() },
            "gram_panchayat"           to etGramPanchayat.text.toString().takeIf { it.isNotEmpty() },
            "village"                  to village,
            "survey_intimation_no"     to etSurveyIntimationNo.text.toString().takeIf { it.isNotEmpty() },
            "crop_name"                to tdCode(tdCrops, spCropName.selectedItemPosition - 1),
            "insurance_unit"           to tdCode(tdInsurance, spInsuranceUnit.selectedItemPosition - 1),
            "other_crop"               to etOtherCrop.text.toString().takeIf { it.isNotEmpty() },
            "any_disease"              to tdCode(tdYesNo, spAnyDisease.selectedItemPosition - 1),
            "disease_name"             to etDiseaseName.text.toString().takeIf { it.isNotEmpty() },
            "farmer_name"              to farmerName,
            "farmer_mobile"            to farmerMobile,
            "farmer_insurance_app_no"  to etFarmerAppNo.text.toString().takeIf { it.isNotEmpty() },
            "farmer_verified"          to (SurveySession.formData["farmer_verified"]?.toString() ?: "skipped"),
            "farmer_phone_verified"    to (SurveySession.formData["farmer_phone"]?.toString() ?: ""),
            "farmer_available"         to tdCode(tdYesNo, spFarmerAvailable.selectedItemPosition - 1),
            "relation_with_farmer"     to etRelationWithFarmer.text.toString().takeIf { it.isNotEmpty() },
            "representative_name"      to etRepresentativeName.text.toString().takeIf { it.isNotEmpty() },
            "representative_mobile"    to etRepresentativeMobile.text.toString().takeIf { it.isNotEmpty() },
            "survey_date"              to etSurveyDate.text.toString().takeIf { it.isNotEmpty() },
            "sowing_date"              to etSowingDate.text.toString().takeIf { it.isNotEmpty() },
            "date_of_intimation"       to etDateOfIntimation.text.toString().takeIf { it.isNotEmpty() },
            "date_of_loss"             to etDateOfLoss.text.toString().takeIf { it.isNotEmpty() },
            "cause_of_event"           to tdCode(tdCauseEvent, spCauseOfEvent.selectedItemPosition - 1),
            "other_cause"              to etOtherCause.text.toString().takeIf { it.isNotEmpty() },
            "crop_stage"               to tdCode(tdCropStages, spCropStage.selectedItemPosition - 1),
            "cropping_pattern"         to tdCode(tdCropPattern, spCroppingPattern.selectedItemPosition - 1),
            "post_harvest"             to tdCode(tdYesNo, spPostHarvest.selectedItemPosition - 1),
            "harvest_date"             to etHarvestDate.text.toString().takeIf { it.isNotEmpty() },
            "crop_situation_field"     to tdCode(tdCropSituationField, spCropSituationField.selectedItemPosition - 1),
            "khasra_no"                to etKhasraNo.text.toString().takeIf { it.isNotEmpty() },
            "total_land_area"          to totalLandArea.toDoubleOrNull(),
            "insured_area"             to insuredArea.toDoubleOrNull(),
            "area_affected_pct"        to etAreaAffectedPct.text.toString().toDoubleOrNull(),
            "loss_pct"                 to etLossPct.text.toString().toDoubleOrNull(),
            "recovery_rate_pct"        to etRecoveryRate.text.toString().toDoubleOrNull(),
            "onfield_condition"        to tdCode(tdOnfield, spOnfieldCondition.selectedItemPosition - 1),
            "govt_officer_name"        to etOfficerName.text.toString().takeIf { it.isNotEmpty() },
            "govt_officer_mobile"      to etOfficerMobile.text.toString().takeIf { it.isNotEmpty() },
            "govt_officer_designation" to etOfficerDesignation.text.toString().takeIf { it.isNotEmpty() },
            "dispute_if_any"           to tdCode(tdYesNo, spDisputeIfAny.selectedItemPosition - 1),
            "remarks"                  to etRemarks.text.toString().takeIf { it.isNotEmpty() },
            "dispute_recording_uri"    to SurveySession.formData["dispute_recording_uri"],
            "capture_lat"              to SurveySession.formData["capture_lat"],
            "capture_lon"              to SurveySession.formData["capture_lon"],
            "field_area_polygon"       to etFieldAreaPolygon.text.toString().toDoubleOrNull(),
        )
    }

    /** Auto-fills farmer mobile from verified OTP and shows verified status */
    private fun autoFillFarmerVerification() {
        val phone    = SurveySession.formData["farmer_phone"]?.toString() ?: ""
        val verified = SurveySession.formData["farmer_verified"]?.toString() ?: ""

        val lblFarmerVerified = view?.findViewById<android.widget.TextView>(R.id.lbl_farmer_verified)
        val etStatus = view?.findViewById<android.widget.EditText>(R.id.et_farmer_verification_status)

        if (verified == "yes" && phone.isNotEmpty()) {
            // Always auto-fill mobile from the OTP-verified number — source of truth
            etFarmerMobile.setText(phone)
            lblFarmerVerified?.text = "✅ Farmer Verified: +91-$phone"
            lblFarmerVerified?.visibility = android.view.View.VISIBLE
            etStatus?.setText("✅ Verified (OTP confirmed)")
            etStatus?.setTextColor(android.graphics.Color.parseColor("#16A34A"))
        } else {
            lblFarmerVerified?.visibility = android.view.View.GONE
            etStatus?.setText("⏭ Skipped")
            etStatus?.setTextColor(android.graphics.Color.parseColor("#888888"))
        }
    }
}