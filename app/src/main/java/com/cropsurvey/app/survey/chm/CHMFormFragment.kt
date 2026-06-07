package com.cropsurvey.app.survey.chm

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.cropsurvey.app.R
import com.cropsurvey.app.config.AppConfig
import com.cropsurvey.app.i18n.TranslatedDropdown
import com.cropsurvey.app.network.ApiClient
import com.cropsurvey.app.utils.GpsHelper
import com.cropsurvey.app.utils.SurveySession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import com.cropsurvey.app.guide.AiGuideOverlay

/**
 * CHM Form – section behaviour mirrors CCEFormFragment exactly:
 *  - All sections collapsed on new survey; expanded on restore
 *  - Section complete → green header + green ✓ badge (only when ALL required fields filled)
 *  - Section incomplete + nextAttempted → red header + red ✗ badge
 *  - Section untouched / Next not yet attempted → grey header, no badge
 *  - NO per-field green tick icons (only label turns green when filled)
 */
class CHMFormFragment : Fragment() {

    // ── Section 1: Basic Information ──────────────────────────────
    private lateinit var etOnlineChmId: EditText
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

    // ── Section 3: Crop Details ───────────────────────────────────
    private lateinit var spCropName: Spinner
    private lateinit var spInsuranceUnit: Spinner
    private lateinit var etCropVariety: EditText
    private lateinit var spCropStage: Spinner
    private lateinit var spCropHealth: Spinner
    private lateinit var etCropCover: EditText
    private lateinit var spSowingMethod: Spinner
    private lateinit var spIrrigationType: Spinner
    private lateinit var spLandType: Spinner

    // ── Section 4: Farmer Information ────────────────────────────
    private lateinit var etFarmerName: EditText
    private lateinit var etFarmerMobile: EditText
    private lateinit var etFarmerAppNo: EditText

    // ── Section 5: Field Parameters ──────────────────────────────
    private lateinit var etFieldAreaPolygon: EditText
    private lateinit var etSowingDate: EditText
    private lateinit var etExpectedHarvest: EditText
    private lateinit var etPlantCountPerSqm: EditText
    private lateinit var etRowSpacing: EditText
    private lateinit var etRidgeDistance: EditText
    private lateinit var etNumIrrigations: EditText
    private lateinit var etFertilizerKgHa: EditText

    // ── Section 6: Pest & Disease ─────────────────────────────────
    private lateinit var cbPestIncidents: CheckBox
    private lateinit var etPestName: EditText

    // ── Section 7: Yield Measurements ────────────────────────────
    private lateinit var etPlantWeight: EditText
    private lateinit var etTuberCount: EditText
    private lateinit var etTuberWeight: EditText
    private lateinit var etPlantWeightGrain: EditText
    private lateinit var etGrainWeight: EditText
    private lateinit var etRemarks: EditText
    private lateinit var tvGpsCoords: TextView

    // ── Label TextViews ───────────────────────────────────────────
    private lateinit var lblOnlineChmId: TextView
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
    private lateinit var lblCropName: TextView
    private lateinit var lblInsuranceUnit: TextView
    private lateinit var lblCropVariety: TextView
    private lateinit var lblCropStage: TextView
    private lateinit var lblCropHealth: TextView
    private lateinit var lblCropCover: TextView
    private lateinit var lblSowingMethod: TextView
    private lateinit var lblIrrigationType: TextView
    private lateinit var lblLandType: TextView
    private lateinit var lblFarmerName: TextView
    private lateinit var lblFarmerMobile: TextView
    private lateinit var lblFarmerAppNo: TextView
    private lateinit var lblSowingDate: TextView
    private lateinit var lblExpectedHarvest: TextView
    private lateinit var lblPlantCountSqm: TextView
    private lateinit var lblRowSpacing: TextView
    private lateinit var lblRidgeDistance: TextView
    private lateinit var lblNumIrrigations: TextView
    private lateinit var lblFertilizerKgHa: TextView
    private lateinit var lblPestName: TextView
    private lateinit var lblPlantWeight: TextView
    private lateinit var lblTuberCount: TextView
    private lateinit var lblTuberWeight: TextView
    private lateinit var lblPlantWeightGrain: TextView
    private lateinit var lblGrainWeight: TextView
    private lateinit var lblRemarks: TextView

    // ── Collapsible section state ─────────────────────────────────
    private val sectionHeaders  = mutableMapOf<Int, View>()
    private val sectionBodies   = mutableMapOf<Int, View>()
    private val sectionArrows   = mutableMapOf<Int, TextView>()
    private val sectionChecks   = mutableMapOf<Int, ImageView>()
    private val sectionExpanded = mutableMapOf<Int, Boolean>()
    private val sectionTouched  = mutableMapOf<Int, Boolean>()
    private var nextAttempted   = false
    private var pestSectionTouchedThisSession = false  // true only when user opens pest section in current session

    private var states       = listOf<String>()
    private var districts    = listOf<String>()
    private var subDistricts = listOf<String>()
    private var isRestoring  = false

    private val colorGreen get() = ContextCompat.getColor(requireContext(), R.color.primary)
    private val colorGrey = 0xFF64748B.toInt()

    // ─────────────────────────────────────────────────────────────────

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_chm_form, container, false)
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
        etFieldAreaPolygon.setText(
            if (area != null) "%.4f".format(area)
            else SurveySession.formData["field_area_polygon"]?.toString() ?: ""
        )
    }

    private fun bindViews(v: View) {
        etOnlineChmId     = v.findViewById(R.id.et_online_chm_id)
        spYear            = v.findViewById(R.id.sp_year)
        spSeason          = v.findViewById(R.id.sp_season)
        spScheme          = v.findViewById(R.id.sp_scheme)
        etOtherScheme     = v.findViewById(R.id.et_other_scheme)
        layoutOtherScheme = v.findViewById(R.id.layout_other_scheme)
        spState           = v.findViewById(R.id.sp_state)
        spDistrict        = v.findViewById(R.id.sp_district)
        spTehsil          = v.findViewById(R.id.sp_tehsil)
        etRevenueCircle   = v.findViewById(R.id.et_revenue_circle)
        etGramPanchayat   = v.findViewById(R.id.et_gram_panchayat)
        etVillage         = v.findViewById(R.id.et_village)
        spCropName        = v.findViewById(R.id.sp_crop_name)
        spInsuranceUnit   = v.findViewById(R.id.sp_insurance_unit)
        etCropVariety     = v.findViewById(R.id.et_crop_variety)
        spCropStage       = v.findViewById(R.id.sp_crop_stage)
        spCropHealth      = v.findViewById(R.id.sp_crop_health)
        etCropCover       = v.findViewById(R.id.et_crop_cover)
        spSowingMethod    = v.findViewById(R.id.sp_sowing_method)
        spIrrigationType  = v.findViewById(R.id.sp_irrigation_type)
        spLandType        = v.findViewById(R.id.sp_land_type)
        etFarmerName      = v.findViewById(R.id.et_farmer_name)
        etFarmerMobile    = v.findViewById(R.id.et_farmer_mobile)
        etFarmerAppNo     = v.findViewById(R.id.et_farmer_app_no)
        etFieldAreaPolygon = v.findViewById(R.id.et_field_area_polygon)
        etSowingDate      = v.findViewById(R.id.et_sowing_date)
        etExpectedHarvest = v.findViewById(R.id.et_expected_harvest)
        etPlantCountPerSqm = v.findViewById(R.id.et_plant_count_sqm)
        etRowSpacing      = v.findViewById(R.id.et_row_spacing)
        etRidgeDistance   = v.findViewById(R.id.et_ridge_distance)
        etNumIrrigations  = v.findViewById(R.id.et_num_irrigations)
        etFertilizerKgHa  = v.findViewById(R.id.et_fertilizer_kg_ha)
        cbPestIncidents   = v.findViewById(R.id.cb_pest_incidents)
        etPestName        = v.findViewById(R.id.et_pest_name)
        etPlantWeight     = v.findViewById(R.id.et_plant_weight)
        etTuberCount      = v.findViewById(R.id.et_tuber_count)
        etTuberWeight     = v.findViewById(R.id.et_tuber_weight)
        etPlantWeightGrain = v.findViewById(R.id.et_plant_weight_grain)
        etGrainWeight     = v.findViewById(R.id.et_grain_weight)
        etRemarks         = v.findViewById(R.id.et_remarks)
        tvGpsCoords       = v.findViewById(R.id.tv_gps_coords)

        lblOnlineChmId    = v.findViewById(R.id.lbl_online_chm_id)
        lblYear           = v.findViewById(R.id.lbl_year)
        lblSeason         = v.findViewById(R.id.lbl_season)
        lblScheme         = v.findViewById(R.id.lbl_scheme)
        lblOtherScheme    = v.findViewById(R.id.lbl_other_scheme)
        lblState          = v.findViewById(R.id.lbl_state)
        lblDistrict       = v.findViewById(R.id.lbl_district)
        lblTehsil         = v.findViewById(R.id.lbl_tehsil)
        lblRevenueCircle  = v.findViewById(R.id.lbl_revenue_circle)
        lblGramPanchayat  = v.findViewById(R.id.lbl_gram_panchayat)
        lblVillage        = v.findViewById(R.id.lbl_village)
        lblCropName       = v.findViewById(R.id.lbl_crop_name)
        lblInsuranceUnit  = v.findViewById(R.id.lbl_insurance_unit)
        lblCropVariety    = v.findViewById(R.id.lbl_crop_variety)
        lblCropStage      = v.findViewById(R.id.lbl_crop_stage)
        lblCropHealth     = v.findViewById(R.id.lbl_crop_health)
        lblCropCover      = v.findViewById(R.id.lbl_crop_cover)
        lblSowingMethod   = v.findViewById(R.id.lbl_sowing_method)
        lblIrrigationType = v.findViewById(R.id.lbl_irrigation_type)
        lblLandType       = v.findViewById(R.id.lbl_land_type)
        lblFarmerName     = v.findViewById(R.id.lbl_farmer_name)
        lblFarmerMobile   = v.findViewById(R.id.lbl_farmer_mobile)
        lblFarmerAppNo    = v.findViewById(R.id.lbl_farmer_app_no)
        lblSowingDate     = v.findViewById(R.id.lbl_sowing_date)
        lblExpectedHarvest = v.findViewById(R.id.lbl_expected_harvest)
        lblPlantCountSqm  = v.findViewById(R.id.lbl_plant_count_sqm)
        lblRowSpacing     = v.findViewById(R.id.lbl_row_spacing)
        lblRidgeDistance  = v.findViewById(R.id.lbl_ridge_distance)
        lblNumIrrigations = v.findViewById(R.id.lbl_num_irrigations)
        lblFertilizerKgHa = v.findViewById(R.id.lbl_fertilizer_kg_ha)
        lblPestName       = v.findViewById(R.id.lbl_pest_name)
        lblPlantWeight    = v.findViewById(R.id.lbl_plant_weight)
        lblTuberCount     = v.findViewById(R.id.lbl_tuber_count)
        lblTuberWeight    = v.findViewById(R.id.lbl_tuber_weight)
        lblPlantWeightGrain = v.findViewById(R.id.lbl_plant_weight_grain)
        lblGrainWeight    = v.findViewById(R.id.lbl_grain_weight)
        lblRemarks        = v.findViewById(R.id.lbl_remarks)
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
        updateFieldUi(etOnlineChmId,    lblOnlineChmId,    R.id.frame_et_online_chm_id)
        updateSpinnerUi(spYear,         lblYear,           R.id.frame_sp_year)
        updateSpinnerUi(spSeason,       lblSeason,         R.id.frame_sp_season)
        updateSpinnerUi(spScheme,       lblScheme,         R.id.frame_sp_scheme)
        updateFieldUi(etOtherScheme,    lblOtherScheme,    R.id.frame_et_other_scheme)
        updateSpinnerUi(spState,        lblState,          R.id.frame_sp_state)
        updateSpinnerUi(spDistrict,     lblDistrict,       R.id.frame_sp_district)
        updateSpinnerUi(spTehsil,       lblTehsil,         R.id.frame_sp_tehsil)
        updateFieldUi(etRevenueCircle,  lblRevenueCircle,  R.id.frame_et_revenue_circle)
        updateFieldUi(etGramPanchayat,  lblGramPanchayat,  R.id.frame_et_gram_panchayat)
        updateFieldUi(etVillage,        lblVillage,        R.id.frame_et_village)
        updateSpinnerUi(spCropName,     lblCropName,       R.id.frame_sp_crop_name)
        updateSpinnerUi(spInsuranceUnit, lblInsuranceUnit, R.id.frame_sp_insurance_unit)
        updateFieldUi(etCropVariety,    lblCropVariety,    R.id.frame_et_crop_variety)
        updateSpinnerUi(spCropStage,    lblCropStage,      R.id.frame_sp_crop_stage)
        updateSpinnerUi(spCropHealth,   lblCropHealth,     R.id.frame_sp_crop_health)
        updateFieldUi(etCropCover,      lblCropCover,      R.id.frame_et_crop_cover)
        updateSpinnerUi(spSowingMethod, lblSowingMethod,   R.id.frame_sp_sowing_method)
        updateSpinnerUi(spIrrigationType, lblIrrigationType, R.id.frame_sp_irrigation_type)
        updateSpinnerUi(spLandType,     lblLandType,       R.id.frame_sp_land_type)
        updateFieldUi(etFarmerName,     lblFarmerName,     R.id.frame_et_farmer_name)
        updateFieldUi(etFarmerMobile,   lblFarmerMobile,   R.id.frame_et_farmer_mobile)
        updateFieldUi(etFarmerAppNo,    lblFarmerAppNo,    R.id.frame_et_farmer_app_no)
        updateFieldUi(etSowingDate,     lblSowingDate,     R.id.frame_et_sowing_date)
        updateFieldUi(etExpectedHarvest, lblExpectedHarvest, R.id.frame_et_expected_harvest)
        updateFieldUi(etPlantCountPerSqm, lblPlantCountSqm, R.id.frame_et_plant_count_sqm)
        updateFieldUi(etRowSpacing,     lblRowSpacing,     R.id.frame_et_row_spacing)
        updateFieldUi(etRidgeDistance,  lblRidgeDistance,  R.id.frame_et_ridge_distance)
        updateFieldUi(etNumIrrigations, lblNumIrrigations, R.id.frame_et_num_irrigations)
        updateFieldUi(etFertilizerKgHa, lblFertilizerKgHa, R.id.frame_et_fertilizer_kg_ha)
        updateFieldUi(etPestName,       lblPestName,       R.id.frame_et_pest_name)
        updateFieldUi(etPlantWeight,    lblPlantWeight,    R.id.frame_et_plant_weight)
        updateFieldUi(etTuberCount,     lblTuberCount,     R.id.frame_et_tuber_count)
        updateFieldUi(etTuberWeight,    lblTuberWeight,    R.id.frame_et_tuber_weight)
        updateFieldUi(etPlantWeightGrain, lblPlantWeightGrain, R.id.frame_et_plant_weight_grain)
        updateFieldUi(etGrainWeight,    lblGrainWeight,    R.id.frame_et_grain_weight)
        updateFieldUi(etRemarks,        lblRemarks,        R.id.frame_et_remarks)
    }

    // ─────────────────────────────────────────────────────────────────
    // Collapsible sections
    // ─────────────────────────────────────────────────────────────────

    private fun setupCollapsibleSections(root: View) {
        for (i in 1..7) {
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
                if (i == 6) pestSectionTouchedThisSession = true
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
            // Show guide step when user opens a section for the first time
            activity?.let { act ->
                when (section) {
                    0 -> AiGuideOverlay.show(act, AiGuideOverlay.Step.FORM_BASIC_INFO)
                    1 -> AiGuideOverlay.show(act, AiGuideOverlay.Step.FORM_LOCATION)
                    2 -> AiGuideOverlay.show(act, AiGuideOverlay.Step.FORM_CROP_DETAILS)
                }
            }
        }

        if (expanded) {
            body.visibility = View.GONE
            arrow.text = "▼"
            sectionExpanded[section] = false
            val isDone    = isSectionDone(section)
            val isTouched = sectionTouched[section] ?: false
            header.setBackgroundResource(when {
                isDone    -> R.drawable.section_header_done_collapsed
                isTouched && nextAttempted -> R.drawable.section_header_error_collapsed
                else      -> R.drawable.section_header_collapsed
            })
        } else {
            body.visibility = View.VISIBLE
            arrow.text = "▲"
            sectionExpanded[section] = true
            val isDone    = isSectionDone(section)
            val isTouched = sectionTouched[section] ?: false
            header.setBackgroundResource(when {
                isDone    -> R.drawable.section_header_done
                isTouched && nextAttempted -> R.drawable.section_header_error
                else      -> R.drawable.section_header_default
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

        etOnlineChmId.addTextChangedListener(watcher(1))
        etVillage.addTextChangedListener(watcher(2))
        etRevenueCircle.addTextChangedListener(watcher(2))
        etGramPanchayat.addTextChangedListener(watcher(2))
        etCropVariety.addTextChangedListener(watcher(3))
        etCropCover.addTextChangedListener(watcher(3))
        etFarmerName.addTextChangedListener(watcher(4))
        etFarmerMobile.addTextChangedListener(watcher(4))
        etFarmerAppNo.addTextChangedListener(watcher(4))
        etSowingDate.addTextChangedListener(watcher(5))
        etExpectedHarvest.addTextChangedListener(watcher(5))
        etPlantCountPerSqm.addTextChangedListener(watcher(5))
        etRowSpacing.addTextChangedListener(watcher(5))
        etRidgeDistance.addTextChangedListener(watcher(5))
        etNumIrrigations.addTextChangedListener(watcher(5))
        etFertilizerKgHa.addTextChangedListener(watcher(5))
        etPestName.addTextChangedListener(watcher(6))
        etPlantWeight.addTextChangedListener(watcher(7))
        etTuberCount.addTextChangedListener(watcher(7))
        etTuberWeight.addTextChangedListener(watcher(7))
        etPlantWeightGrain.addTextChangedListener(watcher(7))
        etGrainWeight.addTextChangedListener(watcher(7))
        etRemarks.addTextChangedListener(watcher(7))

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
                layoutOtherScheme.visibility = if (spScheme.selectedItem == "Others") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        })
        spCropName.onItemSelectedListener      = spinnerListener(3)
        spInsuranceUnit.onItemSelectedListener = spinnerListener(3)
        spCropStage.onItemSelectedListener     = spinnerListener(3)
        spCropHealth.onItemSelectedListener    = spinnerListener(3)
        spSowingMethod.onItemSelectedListener  = spinnerListener(3)
        spIrrigationType.onItemSelectedListener = spinnerListener(3)
        spLandType.onItemSelectedListener      = spinnerListener(3)

        cbPestIncidents.setOnCheckedChangeListener { _, _ ->
            sectionTouched[6] = true
            pestSectionTouchedThisSession = true
            refreshFieldUi()
            refreshSectionStatus()
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Section completion logic
    // ─────────────────────────────────────────────────────────────────

    private fun isSectionDone(section: Int): Boolean = when (section) {
        // GREEN = ALL fields in section filled (required AND optional)
        // S1: online_chm_id(opt), year, season, scheme
        1 -> spYear.selectedItemPosition > 0
                && spSeason.selectedItemPosition > 0
                && spScheme.selectedItemPosition > 0
                && etOnlineChmId.text.isNotBlank()
        // S2: state, district, tehsil, revenue_circle, gram_panchayat, village
        2 -> spState.selectedItemPosition > 0
                && spDistrict.selectedItemPosition > 0
                && spTehsil.selectedItemPosition > 0
                && etRevenueCircle.text.isNotBlank()
                && etGramPanchayat.text.isNotBlank()
                && etVillage.text.isNotBlank()
        // S3: crop_name, insurance_unit, crop_variety, crop_stage, crop_health, crop_cover, sowing_method, irrigation_type, land_type
        3 -> spCropName.selectedItemPosition > 0
                && spInsuranceUnit.selectedItemPosition > 0
                && etCropVariety.text.isNotBlank()
                && spCropStage.selectedItemPosition > 0
                && spCropHealth.selectedItemPosition > 0
                && etCropCover.text.isNotBlank()
                && spSowingMethod.selectedItemPosition > 0
                && spIrrigationType.selectedItemPosition > 0
                && spLandType.selectedItemPosition > 0
        // S4: farmer_name, farmer_mobile, farmer_app_no
        4 -> etFarmerName.text.isNotBlank()
                && etFarmerMobile.text.length >= 10
                && etFarmerAppNo.text.isNotBlank()
        // S5: field_area_polygon, sowing_date, expected_harvest, plant_count, row_spacing, ridge_distance, num_irrigations, fertilizer
        5 -> etFieldAreaPolygon.text.isNotBlank()
                && etSowingDate.text.isNotBlank()
                && etExpectedHarvest.text.isNotBlank()
                && etPlantCountPerSqm.text.isNotBlank()
                && etRowSpacing.text.isNotBlank()
                && etRidgeDistance.text.isNotBlank()
                && etNumIrrigations.text.isNotBlank()
                && etFertilizerKgHa.text.isNotBlank()
        // S6: user must open pest section in THIS session; if pest checked, name required
        6 -> pestSectionTouchedThisSession
                && (if (cbPestIncidents.isChecked) etPestName.text.isNotBlank() else true)
        // S7: plant_weight, tuber_count, tuber_weight, plant_weight_grain, grain_weight, remarks
        7 -> etPlantWeight.text.isNotBlank()
                && etTuberCount.text.isNotBlank()
                && etTuberWeight.text.isNotBlank()
                && etPlantWeightGrain.text.isNotBlank()
                && etGrainWeight.text.isNotBlank()
                && etRemarks.text.isNotBlank()
        else -> false
    }
    fun refreshSectionStatus() {
        for (i in 1..7) {
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
        val allDone = (1..7).all { isSectionDone(it) }
        if (!allDone) {
            val firstIncomplete = (1..7).firstOrNull { !isSectionDone(it) }
            if (firstIncomplete != null && sectionExpanded[firstIncomplete] == false) {
                toggleSection(firstIncomplete)
            }
        }
        return allDone
    }

    // ─────────────────────────────────────────────────────────────────

    private fun set(sp: Spinner, items: List<String>) {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sp.adapter = adapter
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
    private var tdCropHealth  = listOf<TranslatedDropdown.Option>()
    private var tdSowing      = listOf<TranslatedDropdown.Option>()
    private var tdIrrigation  = listOf<TranslatedDropdown.Option>()
    private var tdLandTypes   = listOf<TranslatedDropdown.Option>()

    private fun setupSpinners() {
        val ctx = requireContext()
        tdSeasons    = TranslatedDropdown.seasons(ctx)
        tdSchemes    = TranslatedDropdown.schemes(ctx)
        tdCrops      = TranslatedDropdown.crops(ctx)
        tdInsurance  = TranslatedDropdown.insuranceUnits(ctx)
        tdCropStages = TranslatedDropdown.cropStages(ctx)
        tdCropHealth = TranslatedDropdown.cropHealth(ctx)
        tdSowing     = TranslatedDropdown.sowingMethods(ctx)
        tdIrrigation = TranslatedDropdown.irrigationTypes(ctx)
        tdLandTypes  = TranslatedDropdown.landTypes(ctx)

        set(spYear,          listOf("Select Year")       + AppConfig.YEARS)
        set(spSeason,        listOf(getString(R.string.hint_select)) + tdLabels(tdSeasons))
        set(spScheme,        listOf(getString(R.string.hint_select)) + tdLabels(tdSchemes))
        set(spCropName,      listOf(getString(R.string.hint_select)) + tdLabels(tdCrops))
        set(spInsuranceUnit, listOf(getString(R.string.hint_select)) + tdLabels(tdInsurance))
        set(spCropStage,     listOf(getString(R.string.hint_select)) + tdLabels(tdCropStages))
        set(spCropHealth,    listOf(getString(R.string.hint_select)) + tdLabels(tdCropHealth))
        set(spSowingMethod,  listOf(getString(R.string.hint_select)) + tdLabels(tdSowing))
        set(spIrrigationType,listOf(getString(R.string.hint_select)) + tdLabels(tdIrrigation))
        set(spLandType,      listOf(getString(R.string.hint_select)) + tdLabels(tdLandTypes))
        set(spState,    listOf("Select State"))
        set(spDistrict, listOf("Select District"))
        set(spTehsil,   listOf("Select Tehsil"))

        spState.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (isRestoring) return
                val state = states.getOrNull(pos - 1) ?: return
                SurveySession.formData["state"] = state
                sectionTouched[2] = true
                lifecycleScope.launch {
                    try {
                        val res = withContext(Dispatchers.IO) { ApiClient.service.getDistricts(state) }
                        if (res.isSuccessful) {
                            districts = res.body() ?: emptyList()
                            set(spDistrict, listOf("Select District") + districts)
                        }
                    } catch (e: Exception) {}
                }
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
                lifecycleScope.launch {
                    try {
                        val res = withContext(Dispatchers.IO) { ApiClient.service.getSubDistricts(state, district) }
                        if (res.isSuccessful) {
                            subDistricts = res.body() ?: emptyList()
                            set(spTehsil, listOf("Select Tehsil") + subDistricts)
                        }
                    } catch (e: Exception) {}
                }
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
        listOf(etSowingDate, etExpectedHarvest).forEach { et ->
            et.isFocusable = false
            et.setOnClickListener {
                val cal = Calendar.getInstance()
                DatePickerDialog(requireContext(), { _, y, m, d ->
                    et.setText("%04d-%02d-%02d".format(y, m + 1, d))
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
    }

    private fun loadStates() {
        lifecycleScope.launch {
            try {
                val res = withContext(Dispatchers.IO) { ApiClient.service.getStates() }
                if (res.isSuccessful) {
                    states = res.body() ?: emptyList()
                    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item,
                        listOf("Select State") + states)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spState.adapter = adapter
                    restoreSpinner(spState, SurveySession.formData["state"]?.toString())
                }
            } catch (e: Exception) {}
        }
    }

    private fun captureGps() {
        lifecycleScope.launch {
            val coords = GpsHelper.getCurrentLocation(requireContext())
            if (coords != null) {
                tvGpsCoords.text = GpsHelper.formatCoords(coords)
                SurveySession.formData["kml_lat"] = coords.lat
                SurveySession.formData["kml_lon"] = coords.lon
            }
        }
    }

    private fun restoreFormData() {
        val fd = SurveySession.formData
        if (fd.isEmpty()) return

        etOnlineChmId.setText(fd["online_chm_id"]?.toString() ?: "")
        etFarmerName.setText(fd["farmer_name"]?.toString() ?: "")
        etFarmerMobile.setText(fd["farmer_mobile"]?.toString() ?: "")
        etFarmerAppNo.setText(fd["farmer_application_no"]?.toString() ?: "")
        etVillage.setText(fd["village"]?.toString() ?: "")
        etRevenueCircle.setText(fd["revenue_circle"]?.toString() ?: "")
        etGramPanchayat.setText(fd["gram_panchayat"]?.toString() ?: "")
        etCropVariety.setText(fd["crop_variety"]?.toString() ?: "")
        etCropCover.setText(fd["crop_cover"]?.toString() ?: "")
        etPlantCountPerSqm.setText(fd["plant_count_per_sqm"]?.toString() ?: "")
        etRowSpacing.setText(fd["row_spacing"]?.toString() ?: "")
        etRidgeDistance.setText(fd["ridge_distance_cm"]?.toString() ?: "")
        etNumIrrigations.setText(fd["num_irrigations"]?.toString() ?: "")
        etFertilizerKgHa.setText(fd["fertilizer_kg_ha"]?.toString() ?: "")
        etPestName.setText(fd["pest_disease_name"]?.toString() ?: "")
        etPlantWeight.setText(fd["plant_weight_grams"]?.toString() ?: "")
        etTuberCount.setText(fd["tuber_count"]?.toString() ?: "")
        etTuberWeight.setText(fd["total_tuber_weight_grams"]?.toString() ?: "")
        etPlantWeightGrain.setText(fd["plant_weight_with_grain"]?.toString() ?: "")
        etGrainWeight.setText(fd["grain_only_weight"]?.toString() ?: "")
        etRemarks.setText(fd["remarks"]?.toString() ?: "")
        etOtherScheme.setText(fd["others_scheme"]?.toString() ?: "")
        etSowingDate.setText(fd["sowing_date"]?.toString() ?: "")
        etExpectedHarvest.setText(fd["expected_harvest_date"]?.toString() ?: "")
        cbPestIncidents.isChecked = fd["pest_disease_incidents"] as? Boolean ?: false

        restoreSpinner(spYear,          fd["year"]?.toString())
        tdRestore(spSeason, tdSeasons, fd["season"]?.toString())
        tdRestore(spScheme, tdSchemes, fd["scheme"]?.toString())
        tdRestore(spCropName, tdCrops, fd["crop_name"]?.toString())
        tdRestore(spInsuranceUnit, tdInsurance, fd["insurance_unit"]?.toString())
        tdRestore(spCropStage, tdCropStages, fd["crop_stage"]?.toString())
        tdRestore(spCropHealth, tdCropHealth, fd["crop_health"]?.toString())
        tdRestore(spSowingMethod, tdSowing, fd["sowing_method"]?.toString())
        tdRestore(spIrrigationType, tdIrrigation, fd["irrigation_type"]?.toString())
        tdRestore(spLandType, tdLandTypes, fd["land_type"]?.toString())

        // Only expand all sections if this is an edit/resubmit (has survey_id context)
        // New CHM surveys carry over some fields but should start collapsed
        val isEditRestore = activity?.intent?.getBooleanExtra("is_edit_mode", false) == true ||
                activity?.intent?.getBooleanExtra("is_resubmit", false) == true
        for (i in 1..7) {
            if (isEditRestore) {
                sectionTouched[i] = true
                sectionExpanded[i] = true
                sectionBodies[i]?.visibility = android.view.View.VISIBLE
                sectionArrows[i]?.text = "▲"
            }
            // else: new survey — sections start untouched and collapsed (no green tick)
        }
        refreshFieldUi()
        refreshSectionStatus()

        val savedState    = fd["state"]?.toString()    ?: return
        val savedDistrict = fd["district"]?.toString() ?: return
        val savedTehsil   = fd["tehsil"]?.toString()

        lifecycleScope.launch {
            isRestoring = true
            try {
                val resD = withContext(Dispatchers.IO) { ApiClient.service.getDistricts(savedState) }
                if (resD.isSuccessful) {
                    districts = resD.body() ?: emptyList()
                    set(spDistrict, listOf("Select District") + districts)
                    restoreSpinner(spDistrict, savedDistrict)
                    SurveySession.formData["district"] = savedDistrict
                    if (savedTehsil != null) {
                        val resT = withContext(Dispatchers.IO) {
                            ApiClient.service.getSubDistricts(savedState, savedDistrict)
                        }
                        if (resT.isSuccessful) {
                            subDistricts = resT.body() ?: emptyList()
                            set(spTehsil, listOf("Select Tehsil") + subDistricts)
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

    /** Check only * required fields. If any missing, scroll to it and show error. Returns false if invalid. */
    fun validateRequiredFields(): Boolean {
        // S1: Year*, Season*
        if (spYear.selectedItemPosition == 0) {
            expandAndScrollTo(1, spYear); return false
        }
        if (spSeason.selectedItemPosition == 0) {
            expandAndScrollTo(1, spSeason); return false
        }
        // S2: State*, District*, Village*
        if (spState.selectedItemPosition == 0) {
            expandAndScrollTo(2, spState); return false
        }
        if (spDistrict.selectedItemPosition == 0) {
            expandAndScrollTo(2, spDistrict); return false
        }
        if (etVillage.text.isBlank()) {
            etVillage.error = "Village is required"
            expandAndScrollTo(2, etVillage); return false
        }
        // S3: Crop Name*
        if (spCropName.selectedItemPosition == 0) {
            expandAndScrollTo(3, spCropName); return false
        }
        // S4: Farmer Name*, Mobile Number*
        if (etFarmerName.text.isBlank()) {
            etFarmerName.error = "Farmer name is required"
            expandAndScrollTo(4, etFarmerName); return false
        }
        if (etFarmerMobile.text.length < 10) {
            etFarmerMobile.error = "Valid 10-digit mobile required"
            expandAndScrollTo(4, etFarmerMobile); return false
        }
        return true
    }

    private fun expandAndScrollTo(section: Int, target: android.view.View) {
        // Expand the section if collapsed
        if (sectionExpanded[section] != true) {
            sectionBodies[section]?.visibility = android.view.View.VISIBLE
            sectionExpanded[section] = true
            sectionArrows[section]?.text = "▲"
            refreshSectionStatus()
        }
        // Scroll to the field
        val sv = requireActivity().findViewById<android.widget.ScrollView>(R.id.form_scroll_view)
        sv?.post { sv.smoothScrollTo(0, (target.top - 100).coerceAtLeast(0)) }
        target.requestFocus()
        android.widget.Toast.makeText(requireContext(), "Please fill all required fields (marked *)", android.widget.Toast.LENGTH_SHORT).show()
    }

    /** Collects all current field values WITHOUT validation — used for draft auto-save. */
    fun collectDraftData(): Map<String, Any?> {
        val farmerName   = etFarmerName.text.toString().trim()
        val farmerMobile = etFarmerMobile.text.toString().trim()
        val village      = etVillage.text.toString().trim()

        return mapOf(
            "online_chm_id"            to etOnlineChmId.text.toString().takeIf { it.isNotEmpty() },
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
            "crop_name"                to tdCode(tdCrops, spCropName.selectedItemPosition - 1),
            "insurance_unit"           to tdCode(tdInsurance, spInsuranceUnit.selectedItemPosition - 1),
            "crop_variety"             to etCropVariety.text.toString().takeIf { it.isNotEmpty() },
            "crop_stage"               to tdCode(tdCropStages, spCropStage.selectedItemPosition - 1),
            "crop_health"              to tdCode(tdCropHealth, spCropHealth.selectedItemPosition - 1),
            "crop_cover"               to etCropCover.text.toString().toDoubleOrNull(),
            "sowing_method"            to tdCode(tdSowing, spSowingMethod.selectedItemPosition - 1),
            "irrigation_type"          to tdCode(tdIrrigation, spIrrigationType.selectedItemPosition - 1),
            "land_type"                to tdCode(tdLandTypes, spLandType.selectedItemPosition - 1),
            "farmer_name"              to farmerName.takeIf { it.isNotEmpty() },
            "farmer_mobile"            to farmerMobile.takeIf { it.isNotEmpty() },
            "farmer_application_no"    to etFarmerAppNo.text.toString().takeIf { it.isNotEmpty() },
            "farmer_verified"          to (SurveySession.formData["farmer_verified"]?.toString() ?: "skipped"),
            "farmer_phone_verified"    to (SurveySession.formData["farmer_phone"]?.toString() ?: ""),
            "sowing_date"              to etSowingDate.text.toString().takeIf { it.isNotEmpty() },
            "expected_harvest_date"    to etExpectedHarvest.text.toString().takeIf { it.isNotEmpty() },
            "plant_count_per_sqm"      to etPlantCountPerSqm.text.toString().toIntOrNull(),
            "row_spacing"              to etRowSpacing.text.toString().toDoubleOrNull(),
            "ridge_distance_cm"        to etRidgeDistance.text.toString().toDoubleOrNull(),
            "num_irrigations"          to etNumIrrigations.text.toString().toIntOrNull(),
            "fertilizer_kg_ha"         to etFertilizerKgHa.text.toString().toDoubleOrNull(),
            "pest_disease_incidents"   to cbPestIncidents.isChecked,
            "pest_disease_name"        to etPestName.text.toString().takeIf { it.isNotEmpty() },
            "plant_weight_grams"       to etPlantWeight.text.toString().toDoubleOrNull(),
            "tuber_count"              to etTuberCount.text.toString().toIntOrNull(),
            "total_tuber_weight_grams" to etTuberWeight.text.toString().toDoubleOrNull(),
            "plant_weight_with_grain"  to etPlantWeightGrain.text.toString().toDoubleOrNull(),
            "grain_only_weight"        to etGrainWeight.text.toString().toDoubleOrNull(),
            "remarks"                  to etRemarks.text.toString().takeIf { it.isNotEmpty() },
            "kml_lat"                  to SurveySession.formData["kml_lat"],
            "kml_lon"                  to SurveySession.formData["kml_lon"],
            "field_area_polygon"       to etFieldAreaPolygon.text.toString().toDoubleOrNull(),
        )
    }

    fun collectFormData(): Map<String, Any?> {
        val farmerName   = etFarmerName.text.toString().trim()
        val farmerMobile = etFarmerMobile.text.toString().trim()
        val village      = etVillage.text.toString().trim()
        if (!validateRequiredFields()) return emptyMap()

        return mapOf(
            "online_chm_id"            to etOnlineChmId.text.toString().takeIf { it.isNotEmpty() },
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
            "crop_name"                to tdCode(tdCrops, spCropName.selectedItemPosition - 1),
            "insurance_unit"           to tdCode(tdInsurance, spInsuranceUnit.selectedItemPosition - 1),
            "crop_variety"             to etCropVariety.text.toString().takeIf { it.isNotEmpty() },
            "crop_stage"               to tdCode(tdCropStages, spCropStage.selectedItemPosition - 1),
            "crop_health"              to tdCode(tdCropHealth, spCropHealth.selectedItemPosition - 1),
            "crop_cover"               to etCropCover.text.toString().toDoubleOrNull(),
            "sowing_method"            to tdCode(tdSowing, spSowingMethod.selectedItemPosition - 1),
            "irrigation_type"          to tdCode(tdIrrigation, spIrrigationType.selectedItemPosition - 1),
            "land_type"                to tdCode(tdLandTypes, spLandType.selectedItemPosition - 1),
            "farmer_name"              to farmerName,
            "farmer_mobile"            to farmerMobile,
            "farmer_application_no"    to etFarmerAppNo.text.toString().takeIf { it.isNotEmpty() },
            "farmer_verified"          to (SurveySession.formData["farmer_verified"]?.toString() ?: "skipped"),
            "farmer_phone_verified"    to (SurveySession.formData["farmer_phone"]?.toString() ?: ""),
            "sowing_date"              to etSowingDate.text.toString().takeIf { it.isNotEmpty() },
            "expected_harvest_date"    to etExpectedHarvest.text.toString().takeIf { it.isNotEmpty() },
            "plant_count_per_sqm"      to etPlantCountPerSqm.text.toString().toIntOrNull(),
            "row_spacing"              to etRowSpacing.text.toString().toDoubleOrNull(),
            "ridge_distance_cm"        to etRidgeDistance.text.toString().toDoubleOrNull(),
            "num_irrigations"          to etNumIrrigations.text.toString().toIntOrNull(),
            "fertilizer_kg_ha"         to etFertilizerKgHa.text.toString().toDoubleOrNull(),
            "pest_disease_incidents"   to cbPestIncidents.isChecked,
            "pest_disease_name"        to etPestName.text.toString().takeIf { it.isNotEmpty() },
            "plant_weight_grams"       to etPlantWeight.text.toString().toDoubleOrNull(),
            "tuber_count"              to etTuberCount.text.toString().toIntOrNull(),
            "total_tuber_weight_grams" to etTuberWeight.text.toString().toDoubleOrNull(),
            "plant_weight_with_grain"  to etPlantWeightGrain.text.toString().toDoubleOrNull(),
            "grain_only_weight"        to etGrainWeight.text.toString().toDoubleOrNull(),
            "remarks"                  to etRemarks.text.toString().takeIf { it.isNotEmpty() },
            "kml_lat"                  to SurveySession.formData["kml_lat"],
            "kml_lon"                  to SurveySession.formData["kml_lon"],
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
            // Always auto-fill mobile from the OTP-verified number — it is the
            // source of truth; user should not need to type it again
            etFarmerMobile.setText(phone)
            // Show green verified label
            lblFarmerVerified?.text = "✅ Farmer Verified: +91-$phone"
            lblFarmerVerified?.visibility = android.view.View.VISIBLE
            // Set status field
            etStatus?.setText("✅ Verified (OTP confirmed)")
            etStatus?.setTextColor(android.graphics.Color.parseColor("#16A34A"))
        } else {
            // Skipped
            lblFarmerVerified?.visibility = android.view.View.GONE
            etStatus?.setText("⏭ Skipped")
            etStatus?.setTextColor(android.graphics.Color.parseColor("#888888"))
        }
    }
}