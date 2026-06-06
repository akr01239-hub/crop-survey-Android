package com.cropsurvey.app.survey.cce

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.TextWatcher
import android.text.Editable
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

/**
 * CCE Form – clean rules:
 *  - All sections collapsed on new survey; expanded on restore
 *  - Section header: default (all-rounded) when collapsed, top-rounded when expanded
 *  - Section complete → green header + green ✓ badge (right side of header)
 *  - Section incomplete + touched → red header + red ✗ badge
 *  - Section untouched → grey header, no badge
 *  - NO per-field green tick icons (only label turns green when filled)
 *  - Yield auto-calculates whenever dry grain / plot size / moisture changes
 */
class CCEFormFragment : Fragment() {

    // ── Section 1: Basic Info ─────────────────────────────────────
    private lateinit var spYear: Spinner
    private lateinit var spSeason: Spinner
    private lateinit var spScheme: Spinner
    private lateinit var etOtherScheme: EditText
    private lateinit var layoutOtherScheme: View
    private lateinit var etCceNumber: EditText
    private lateinit var etCceDate: EditText
    private lateinit var etExperimentId: EditText

    // ── Section 2: Location ───────────────────────────────────────
    private lateinit var spState: Spinner
    private lateinit var spDistrict: Spinner
    private lateinit var spTehsil: Spinner
    private lateinit var etRevenueCircle: EditText
    private lateinit var etGramPanchayat: EditText
    private lateinit var etVillage: EditText

    // ── Section 3: Farmer ─────────────────────────────────────────
    private lateinit var etFarmerName: EditText
    private lateinit var etFarmerMobile: EditText
    private lateinit var etFarmerAadhaarLast4: EditText
    private lateinit var etFarmerAppNo: EditText
    private lateinit var etKhasraNo: EditText

    // ── Section 4: Crop Details ───────────────────────────────────
    private lateinit var spCropName: Spinner
    private lateinit var etCropVariety: EditText
    private lateinit var spInsuranceUnit: Spinner
    private lateinit var etSowingDate: EditText
    private lateinit var etExpectedHarvest: EditText
    private lateinit var spCropStage: Spinner
    private lateinit var spIrrigationType: Spinner
    private lateinit var spLandType: Spinner

    // ── Section 5: CCE Plot ───────────────────────────────────────
    private lateinit var etFieldAreaPolygon: EditText
    private lateinit var spPlotSize: Spinner
    private lateinit var etCustomPlotSize: EditText
    private lateinit var layoutCustomPlot: View
    private lateinit var spSamplingMethod: Spinner
    private lateinit var spCropConditionPlot: Spinner
    private lateinit var etHarvestingDate: EditText
    private lateinit var spThreshingMethod: Spinner
    private lateinit var etThreshingDate: EditText

    // ── Section 6: Yield Measurements ────────────────────────────
    private lateinit var etFreshBiomass: EditText
    private lateinit var etDryBiomass: EditText
    private lateinit var etFreshGrain: EditText
    private lateinit var etDryGrain: EditText
    private lateinit var etMoisture: EditText
    private lateinit var tvYieldEstimate: TextView

    // ── Section 7: Witnesses ─────────────────────────────────────
    private lateinit var spWitnessType: Spinner
    private lateinit var etIcRepName: EditText
    private lateinit var etIcRepMobile: EditText
    private lateinit var etRevenueOfficer: EditText
    private lateinit var cbFarmerPresent: CheckBox

    // ── Section 8: Remarks & GPS ──────────────────────────────────
    private lateinit var spOnfieldCondition: Spinner
    private lateinit var etRemarks: EditText
    private lateinit var tvGpsCoords: TextView

    // ── Label TextViews ───────────────────────────────────────────
    private lateinit var lblYear: TextView
    private lateinit var lblSeason: TextView
    private lateinit var lblScheme: TextView
    private lateinit var lblOtherScheme: TextView
    private lateinit var lblCceNumber: TextView
    private lateinit var lblCceDate: TextView
    private lateinit var lblExperimentId: TextView
    private lateinit var lblState: TextView
    private lateinit var lblDistrict: TextView
    private lateinit var lblTehsil: TextView
    private lateinit var lblRevenueCircle: TextView
    private lateinit var lblGramPanchayat: TextView
    private lateinit var lblVillage: TextView
    private lateinit var lblFarmerName: TextView
    private lateinit var lblFarmerMobile: TextView
    private lateinit var lblFarmerAppNo: TextView
    private lateinit var lblFarmerAadhaar: TextView
    private lateinit var lblKhasra: TextView
    private lateinit var lblCropName: TextView
    private lateinit var lblCropVariety: TextView
    private lateinit var lblInsuranceUnit: TextView
    private lateinit var lblSowingDate: TextView
    private lateinit var lblExpectedHarvest: TextView
    private lateinit var lblCropStage: TextView
    private lateinit var lblIrrigationType: TextView
    private lateinit var lblLandType: TextView
    private lateinit var lblFieldArea: TextView
    private lateinit var lblPlotSize: TextView
    private lateinit var lblCustomPlot: TextView
    private lateinit var lblSamplingMethod: TextView
    private lateinit var lblCropConditionPlot: TextView
    private lateinit var lblHarvestingDate: TextView
    private lateinit var lblThreshingMethod: TextView
    private lateinit var lblThreshingDate: TextView
    private lateinit var lblFreshBiomass: TextView
    private lateinit var lblDryBiomass: TextView
    private lateinit var lblFreshGrain: TextView
    private lateinit var lblDryGrain: TextView
    private lateinit var lblMoisture: TextView
    private lateinit var lblWitnessType: TextView
    private lateinit var lblIcRepName: TextView
    private lateinit var lblIcRepMobile: TextView
    private lateinit var lblRevenueOfficer: TextView
    private lateinit var lblOnfieldCondition: TextView
    private lateinit var lblRemarks: TextView

    // ── Collapsible section state ─────────────────────────────────
    private val sectionHeaders  = mutableMapOf<Int, View>()
    private val sectionBodies   = mutableMapOf<Int, View>()
    private val sectionArrows   = mutableMapOf<Int, TextView>()
    private val sectionChecks   = mutableMapOf<Int, ImageView>()
    // true = body visible (expanded), false = collapsed
    private val sectionExpanded = mutableMapOf<Int, Boolean>()
    // only show error state after user has touched the section
    private val sectionTouched  = mutableMapOf<Int, Boolean>()

    private var states = listOf<String>()
    private var districts = listOf<String>()
    private var subDistricts = listOf<String>()
    private var isRestoring = false

    private val colorGreen get() = ContextCompat.getColor(requireContext(), R.color.primary)
    private val colorGrey  = 0xFF64748B.toInt()

    // ─────────────────────────────────────────────────────────────────

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_cce_form, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupCollapsibleSections(view)   // all collapsed, untouched
        setupSpinners()
        setupDatePickers()
        setupYieldWatchers()
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
        spYear               = v.findViewById(R.id.sp_year)
        spSeason             = v.findViewById(R.id.sp_season)
        spScheme             = v.findViewById(R.id.sp_scheme)
        etOtherScheme        = v.findViewById(R.id.et_other_scheme)
        layoutOtherScheme    = v.findViewById(R.id.layout_other_scheme)
        etCceNumber          = v.findViewById(R.id.et_cce_number)
        etCceDate            = v.findViewById(R.id.et_cce_date)
        etExperimentId       = v.findViewById(R.id.et_experiment_id)
        spState              = v.findViewById(R.id.sp_state)
        spDistrict           = v.findViewById(R.id.sp_district)
        spTehsil             = v.findViewById(R.id.sp_tehsil)
        etRevenueCircle      = v.findViewById(R.id.et_revenue_circle)
        etGramPanchayat      = v.findViewById(R.id.et_gram_panchayat)
        etVillage            = v.findViewById(R.id.et_village)
        etFarmerName         = v.findViewById(R.id.et_farmer_name)
        etFarmerMobile       = v.findViewById(R.id.et_farmer_mobile)
        etFarmerAadhaarLast4 = v.findViewById(R.id.et_farmer_aadhaar_last4)
        etFarmerAppNo        = v.findViewById(R.id.et_farmer_app_no)
        etKhasraNo           = v.findViewById(R.id.et_khasra_no)
        spCropName           = v.findViewById(R.id.sp_crop_name)
        etCropVariety        = v.findViewById(R.id.et_crop_variety)
        spInsuranceUnit      = v.findViewById(R.id.sp_insurance_unit)
        etSowingDate         = v.findViewById(R.id.et_sowing_date)
        etExpectedHarvest    = v.findViewById(R.id.et_expected_harvest)
        spCropStage          = v.findViewById(R.id.sp_crop_stage)
        spIrrigationType     = v.findViewById(R.id.sp_irrigation_type)
        spLandType           = v.findViewById(R.id.sp_land_type)
        etFieldAreaPolygon   = v.findViewById(R.id.et_field_area_polygon)
        spPlotSize           = v.findViewById(R.id.sp_plot_size)
        etCustomPlotSize     = v.findViewById(R.id.et_custom_plot_size)
        layoutCustomPlot     = v.findViewById(R.id.layout_custom_plot)
        spSamplingMethod     = v.findViewById(R.id.sp_sampling_method)
        spCropConditionPlot  = v.findViewById(R.id.sp_crop_condition_plot)
        etHarvestingDate     = v.findViewById(R.id.et_harvesting_date)
        spThreshingMethod    = v.findViewById(R.id.sp_threshing_method)
        etThreshingDate      = v.findViewById(R.id.et_threshing_date)
        etFreshBiomass       = v.findViewById(R.id.et_fresh_biomass)
        etDryBiomass         = v.findViewById(R.id.et_dry_biomass)
        etFreshGrain         = v.findViewById(R.id.et_fresh_grain)
        etDryGrain           = v.findViewById(R.id.et_dry_grain)
        etMoisture           = v.findViewById(R.id.et_moisture)
        tvYieldEstimate      = v.findViewById(R.id.tv_yield_estimate)
        spWitnessType        = v.findViewById(R.id.sp_witness_type)
        etIcRepName          = v.findViewById(R.id.et_ic_rep_name)
        etIcRepMobile        = v.findViewById(R.id.et_ic_rep_mobile)
        etRevenueOfficer     = v.findViewById(R.id.et_revenue_officer)
        cbFarmerPresent      = v.findViewById(R.id.cb_farmer_present)
        spOnfieldCondition   = v.findViewById(R.id.sp_onfield_condition)
        etRemarks            = v.findViewById(R.id.et_remarks)
        tvGpsCoords          = v.findViewById(R.id.tv_gps_coords)

        lblYear              = v.findViewById(R.id.lbl_year)
        lblSeason            = v.findViewById(R.id.lbl_season)
        lblScheme            = v.findViewById(R.id.lbl_scheme)
        lblOtherScheme       = v.findViewById(R.id.lbl_other_scheme)
        lblCceNumber         = v.findViewById(R.id.lbl_cce_number)
        lblCceDate           = v.findViewById(R.id.lbl_cce_date)
        lblExperimentId      = v.findViewById(R.id.lbl_experiment_id)
        lblState             = v.findViewById(R.id.lbl_state)
        lblDistrict          = v.findViewById(R.id.lbl_district)
        lblTehsil            = v.findViewById(R.id.lbl_tehsil)
        lblRevenueCircle     = v.findViewById(R.id.lbl_revenue_circle)
        lblGramPanchayat     = v.findViewById(R.id.lbl_gram_panchayat)
        lblVillage           = v.findViewById(R.id.lbl_village)
        lblFarmerName        = v.findViewById(R.id.lbl_farmer_name)
        lblFarmerMobile      = v.findViewById(R.id.lbl_farmer_mobile)
        lblFarmerAppNo       = v.findViewById(R.id.lbl_farmer_app_no)
        lblFarmerAadhaar     = v.findViewById(R.id.lbl_farmer_aadhaar)
        lblKhasra            = v.findViewById(R.id.lbl_khasra)
        lblCropName          = v.findViewById(R.id.lbl_crop_name)
        lblCropVariety       = v.findViewById(R.id.lbl_crop_variety)
        lblInsuranceUnit     = v.findViewById(R.id.lbl_insurance_unit)
        lblSowingDate        = v.findViewById(R.id.lbl_sowing_date)
        lblExpectedHarvest   = v.findViewById(R.id.lbl_expected_harvest)
        lblCropStage         = v.findViewById(R.id.lbl_crop_stage)
        lblIrrigationType    = v.findViewById(R.id.lbl_irrigation_type)
        lblLandType          = v.findViewById(R.id.lbl_land_type)
        lblFieldArea         = v.findViewById(R.id.lbl_field_area)
        lblPlotSize          = v.findViewById(R.id.lbl_plot_size)
        lblCustomPlot        = v.findViewById(R.id.lbl_custom_plot)
        lblSamplingMethod    = v.findViewById(R.id.lbl_sampling_method)
        lblCropConditionPlot = v.findViewById(R.id.lbl_crop_condition_plot)
        lblHarvestingDate    = v.findViewById(R.id.lbl_harvesting_date)
        lblThreshingMethod   = v.findViewById(R.id.lbl_threshing_method)
        lblThreshingDate     = v.findViewById(R.id.lbl_threshing_date)
        lblFreshBiomass      = v.findViewById(R.id.lbl_fresh_biomass)
        lblDryBiomass        = v.findViewById(R.id.lbl_dry_biomass)
        lblFreshGrain        = v.findViewById(R.id.lbl_fresh_grain)
        lblDryGrain          = v.findViewById(R.id.lbl_dry_grain)
        lblMoisture          = v.findViewById(R.id.lbl_moisture)
        lblWitnessType       = v.findViewById(R.id.lbl_witness_type)
        lblIcRepName         = v.findViewById(R.id.lbl_ic_rep_name)
        lblIcRepMobile       = v.findViewById(R.id.lbl_ic_rep_mobile)
        lblRevenueOfficer    = v.findViewById(R.id.lbl_revenue_officer)
        lblOnfieldCondition  = v.findViewById(R.id.lbl_onfield_condition)
        lblRemarks           = v.findViewById(R.id.lbl_remarks)
    }

    // ─────────────────────────────────────────────────────────────────
    // Field-level green border + label helpers (NO per-field tick icon)
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
        updateSpinnerUi(spYear,              lblYear,              R.id.frame_sp_year)
        updateSpinnerUi(spSeason,            lblSeason,            R.id.frame_sp_season)
        updateSpinnerUi(spScheme,            lblScheme,            R.id.frame_sp_scheme)
        updateFieldUi(etOtherScheme,         lblOtherScheme,       R.id.frame_et_other_scheme)
        updateFieldUi(etCceNumber,           lblCceNumber,         R.id.frame_et_cce_number)
        updateFieldUi(etCceDate,             lblCceDate,           R.id.frame_et_cce_date)
        updateFieldUi(etExperimentId,        lblExperimentId,      R.id.frame_et_experiment_id)
        updateSpinnerUi(spState,             lblState,             R.id.frame_sp_state)
        updateSpinnerUi(spDistrict,          lblDistrict,          R.id.frame_sp_district)
        updateSpinnerUi(spTehsil,            lblTehsil,            R.id.frame_sp_tehsil)
        updateFieldUi(etRevenueCircle,       lblRevenueCircle,     R.id.frame_et_revenue_circle)
        updateFieldUi(etGramPanchayat,       lblGramPanchayat,     R.id.frame_et_gram_panchayat)
        updateFieldUi(etVillage,             lblVillage,           R.id.frame_et_village)
        updateFieldUi(etFarmerName,          lblFarmerName,        R.id.frame_et_farmer_name)
        updateFieldUi(etFarmerMobile,        lblFarmerMobile,      R.id.frame_et_farmer_mobile)
        updateFieldUi(etFarmerAppNo,         lblFarmerAppNo,       R.id.frame_et_farmer_app_no)
        updateFieldUi(etFarmerAadhaarLast4,  lblFarmerAadhaar,     R.id.frame_et_farmer_aadhaar_last4)
        updateFieldUi(etKhasraNo,            lblKhasra,            R.id.frame_et_khasra_no)
        updateSpinnerUi(spCropName,          lblCropName,          R.id.frame_sp_crop_name)
        updateFieldUi(etCropVariety,         lblCropVariety,       R.id.frame_et_crop_variety)
        updateSpinnerUi(spInsuranceUnit,     lblInsuranceUnit,     R.id.frame_sp_insurance_unit)
        updateFieldUi(etSowingDate,          lblSowingDate,        R.id.frame_et_sowing_date)
        updateFieldUi(etExpectedHarvest,     lblExpectedHarvest,   R.id.frame_et_expected_harvest)
        updateSpinnerUi(spCropStage,         lblCropStage,         R.id.frame_sp_crop_stage)
        updateSpinnerUi(spIrrigationType,    lblIrrigationType,    R.id.frame_sp_irrigation_type)
        updateSpinnerUi(spLandType,          lblLandType,          R.id.frame_sp_land_type)
        updateFieldUi(etFieldAreaPolygon,    lblFieldArea,         R.id.frame_et_field_area_polygon)
        updateSpinnerUi(spPlotSize,          lblPlotSize,          R.id.frame_sp_plot_size)
        updateFieldUi(etCustomPlotSize,      lblCustomPlot,        R.id.frame_et_custom_plot_size)
        updateSpinnerUi(spSamplingMethod,    lblSamplingMethod,    R.id.frame_sp_sampling_method)
        updateSpinnerUi(spCropConditionPlot, lblCropConditionPlot, R.id.frame_sp_crop_condition_plot)
        updateFieldUi(etHarvestingDate,      lblHarvestingDate,    R.id.frame_et_harvesting_date)
        updateSpinnerUi(spThreshingMethod,   lblThreshingMethod,   R.id.frame_sp_threshing_method)
        updateFieldUi(etThreshingDate,       lblThreshingDate,     R.id.frame_et_threshing_date)
        updateFieldUi(etFreshBiomass,        lblFreshBiomass,      R.id.frame_et_fresh_biomass)
        updateFieldUi(etDryBiomass,          lblDryBiomass,        R.id.frame_et_dry_biomass)
        updateFieldUi(etFreshGrain,          lblFreshGrain,        R.id.frame_et_fresh_grain)
        updateFieldUi(etDryGrain,            lblDryGrain,          R.id.frame_et_dry_grain)
        updateFieldUi(etMoisture,            lblMoisture,          R.id.frame_et_moisture)
        updateSpinnerUi(spWitnessType,       lblWitnessType,       R.id.frame_sp_witness_type)
        updateFieldUi(etIcRepName,           lblIcRepName,         R.id.frame_et_ic_rep_name)
        updateFieldUi(etIcRepMobile,         lblIcRepMobile,       R.id.frame_et_ic_rep_mobile)
        updateFieldUi(etRevenueOfficer,      lblRevenueOfficer,    R.id.frame_et_revenue_officer)
        updateSpinnerUi(spOnfieldCondition,  lblOnfieldCondition,  R.id.frame_sp_onfield_condition)
        updateFieldUi(etRemarks,             lblRemarks,           R.id.frame_et_remarks)
    }

    // ─────────────────────────────────────────────────────────────────
    // Collapsible sections – all collapsed and untouched at start
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
            sectionExpanded[i] = false   // collapsed by default
            sectionTouched[i]  = false   // no red cross on fresh form

            // Start state: collapsed, rounded all corners, no badge
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

        if (expanded) {
            // Collapse
            body.visibility = View.GONE
            arrow.text = "▼"
            sectionExpanded[section] = false
            // When collapsed, header is fully rounded (standalone pill)
            // Preserve done/error tint but use collapsed shape
            val isDone = isSectionDone(section)
            val isTouched = sectionTouched[section] ?: false
            header.setBackgroundResource(when {
                isDone    -> R.drawable.section_header_done_collapsed
                isTouched -> R.drawable.section_header_error_collapsed
                else      -> R.drawable.section_header_collapsed
            })
        } else {
            // Expand
            body.visibility = View.VISIBLE
            arrow.text = "▲"
            sectionExpanded[section] = true
            // When expanded, header has rounded top only (joins the body below)
            val isDone = isSectionDone(section)
            val isTouched = sectionTouched[section] ?: false
            header.setBackgroundResource(when {
                isDone    -> R.drawable.section_header_done
                isTouched -> R.drawable.section_header_error
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
        etCceNumber.addTextChangedListener(watcher(1))
        etCceDate.addTextChangedListener(watcher(1))
        etExperimentId.addTextChangedListener(watcher(1))
        etVillage.addTextChangedListener(watcher(2))
        etRevenueCircle.addTextChangedListener(watcher(2))
        etGramPanchayat.addTextChangedListener(watcher(2))
        etFarmerName.addTextChangedListener(watcher(3))
        etFarmerMobile.addTextChangedListener(watcher(3))
        etFarmerAadhaarLast4.addTextChangedListener(watcher(3))
        etFarmerAppNo.addTextChangedListener(watcher(3))
        etKhasraNo.addTextChangedListener(watcher(3))
        etCropVariety.addTextChangedListener(watcher(4))
        etSowingDate.addTextChangedListener(watcher(4))
        etExpectedHarvest.addTextChangedListener(watcher(4))
        // etFieldAreaPolygon is auto-filled from the polygon map — NOT user-typed,
        // so it must NOT be in the watcher or it will set sectionTouched[5]=true
        // the moment the area is injected, causing a false red ✗ on section 5.
        etHarvestingDate.addTextChangedListener(watcher(5))
        etThreshingDate.addTextChangedListener(watcher(5))
        etCustomPlotSize.addTextChangedListener(watcher(5))
        etFreshBiomass.addTextChangedListener(watcher(6))
        etDryBiomass.addTextChangedListener(watcher(6))
        etFreshGrain.addTextChangedListener(watcher(6))
        etDryGrain.addTextChangedListener(watcher(6))
        etMoisture.addTextChangedListener(watcher(6))
        etIcRepName.addTextChangedListener(watcher(7))
        etIcRepMobile.addTextChangedListener(watcher(7))
        etRevenueOfficer.addTextChangedListener(watcher(7))
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

        spYear.onItemSelectedListener             = spinnerListener(1)
        spSeason.onItemSelectedListener           = spinnerListener(1)
        spScheme.onItemSelectedListener           = spinnerListener(1, object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                layoutOtherScheme.visibility = if (tdCode(tdSchemes, spScheme.selectedItemPosition - 1) == "Others") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        })
        spInsuranceUnit.onItemSelectedListener    = spinnerListener(4)
        spCropName.onItemSelectedListener         = spinnerListener(4)
        spCropStage.onItemSelectedListener        = spinnerListener(4)
        spIrrigationType.onItemSelectedListener   = spinnerListener(4)
        spLandType.onItemSelectedListener         = spinnerListener(4)
        spPlotSize.onItemSelectedListener         = spinnerListener(5, object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                layoutCustomPlot.visibility = if (spPlotSize.selectedItem == "Custom") View.VISIBLE else View.GONE
                updateYieldEstimate()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        })
        spSamplingMethod.onItemSelectedListener   = spinnerListener(5)
        spCropConditionPlot.onItemSelectedListener = spinnerListener(5)
        spThreshingMethod.onItemSelectedListener  = spinnerListener(5)
        spWitnessType.onItemSelectedListener      = spinnerListener(7)
        spOnfieldCondition.onItemSelectedListener = spinnerListener(8)

        cbFarmerPresent.setOnCheckedChangeListener { _, _ ->
            sectionTouched[7] = true
            refreshFieldUi()
            refreshSectionStatus()
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Section completion logic
    // ─────────────────────────────────────────────────────────────────

    private fun isSectionDone(section: Int): Boolean = when (section) {
        // GREEN = ALL fields in section filled (required AND optional)
        // S1: year, season, scheme, cce_number, cce_date, experiment_id
        1 -> spYear.selectedItemPosition > 0
                && spSeason.selectedItemPosition > 0
                && spScheme.selectedItemPosition > 0
                && (spScheme.selectedItem?.toString() != "Others" || etOtherScheme.text.isNotBlank())
                && etCceNumber.text.isNotBlank()
                && etCceDate.text.isNotBlank()
                && etExperimentId.text.isNotBlank()
        // S2: state, district, tehsil, revenue_circle, gram_panchayat, village
        2 -> spState.selectedItemPosition > 0
                && spDistrict.selectedItemPosition > 0
                && spTehsil.selectedItemPosition > 0
                && etRevenueCircle.text.isNotBlank()
                && etGramPanchayat.text.isNotBlank()
                && etVillage.text.isNotBlank()
        // S3: farmer_name, farmer_mobile, farmer_app_no, aadhaar_last4, khasra_no
        3 -> etFarmerName.text.isNotBlank()
                && etFarmerMobile.text.length >= 10
                && etFarmerAppNo.text.isNotBlank()
                && etFarmerAadhaarLast4.text.isNotBlank()
                && etKhasraNo.text.isNotBlank()
        // S4: crop_name, crop_variety, insurance_unit, sowing_date, expected_harvest, crop_stage, irrigation_type, land_type
        4 -> spCropName.selectedItemPosition > 0
                && etCropVariety.text.isNotBlank()
                && spInsuranceUnit.selectedItemPosition > 0
                && etSowingDate.text.isNotBlank()
                && etExpectedHarvest.text.isNotBlank()
                && spCropStage.selectedItemPosition > 0
                && spIrrigationType.selectedItemPosition > 0
                && spLandType.selectedItemPosition > 0
        // S5: field_area, plot_size, sampling_method, crop_condition, harvesting_date, threshing_method, threshing_date
        5 -> etFieldAreaPolygon.text.isNotBlank()
                && spPlotSize.selectedItemPosition > 0
                && (spPlotSize.selectedItem?.toString() != "Custom" || etCustomPlotSize.text.isNotBlank())
                && spSamplingMethod.selectedItemPosition > 0
                && spCropConditionPlot.selectedItemPosition > 0
                && etHarvestingDate.text.isNotBlank()
                && spThreshingMethod.selectedItemPosition > 0
                && etThreshingDate.text.isNotBlank()
        // S6: fresh_biomass, dry_biomass, fresh_grain, dry_grain, moisture
        6 -> etFreshBiomass.text.isNotBlank()
                && etDryBiomass.text.isNotBlank()
                && etFreshGrain.text.isNotBlank()
                && etDryGrain.text.isNotBlank()
                && etMoisture.text.isNotBlank()
        // S7: witness_type, ic_rep_name, ic_rep_mobile, revenue_officer, farmer_present(checkbox always has value)
        7 -> spWitnessType.selectedItemPosition > 0
                && etIcRepName.text.isNotBlank()
                && etIcRepMobile.text.length >= 10
                && etRevenueOfficer.text.isNotBlank()
        // S8: onfield_condition, remarks
        8 -> spOnfieldCondition.selectedItemPosition > 0
                && etRemarks.text.isNotBlank()
        else -> false
    }
    fun refreshSectionStatus() {
        for (i in 1..8) {
            updateSectionUi(i, isSectionDone(i))
        }
    }

    // true once the user has tapped NEXT — only then do we show red ✗
    private var nextAttempted = false

    private fun updateSectionUi(section: Int, isDone: Boolean) {
        val header   = sectionHeaders[section] ?: return
        val check    = sectionChecks[section]  ?: return
        val expanded = sectionExpanded[section] ?: false

        when {
            isDone -> {
                // Always show green ✓ as soon as the section is complete
                header.setBackgroundResource(
                    if (expanded) R.drawable.section_header_done else R.drawable.section_header_done_collapsed)
                check.visibility = View.VISIBLE
                check.setBackgroundResource(R.drawable.circle_green)
                check.setImageResource(R.drawable.ic_check_white)
            }
            nextAttempted -> {
                // Only show red ✗ after the user has tried to go to Next
                header.setBackgroundResource(
                    if (expanded) R.drawable.section_header_error else R.drawable.section_header_error_collapsed)
                check.visibility = View.VISIBLE
                check.setBackgroundResource(R.drawable.circle_red)
                check.setImageResource(R.drawable.ic_close_white)
            }
            else -> {
                // Untouched / incomplete but Next not yet attempted — plain grey, no badge
                header.setBackgroundResource(
                    if (expanded) R.drawable.section_header_default else R.drawable.section_header_collapsed)
                check.visibility = View.GONE
            }
        }
    }

    /**
     * Called by SurveyFormActivity when the user taps NEXT.
     * Marks every section as "attempted" so incomplete ones go red,
     * and returns false if any required section is incomplete (so the
     * activity can block navigation and show a toast).
     */
    fun markAllTouchedAndValidate(): Boolean {
        nextAttempted = true
        refreshSectionStatus()
        val allDone = (1..8).all { isSectionDone(it) }
        if (!allDone) {
            // Scroll to (expand) the first incomplete section so the user sees it
            val firstIncomplete = (1..8).firstOrNull { !isSectionDone(it) }
            if (firstIncomplete != null && sectionExpanded[firstIncomplete] == false) {
                toggleSection(firstIncomplete)
            }
        }
        return allDone
    }

    // ─────────────────────────────────────────────────────────────────

    private fun setSpinner(sp: Spinner, items: List<String>) {
        val a = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, items)
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sp.adapter = a
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
    // ── Translated dropdown lists (populated once per fragment creation) ──────
    private var tdSeasons     = listOf<TranslatedDropdown.Option>()
    private var tdSchemes     = listOf<TranslatedDropdown.Option>()
    private var tdCrops       = listOf<TranslatedDropdown.Option>()
    private var tdInsurance   = listOf<TranslatedDropdown.Option>()
    private var tdCropStages  = listOf<TranslatedDropdown.Option>()
    private var tdIrrigation  = listOf<TranslatedDropdown.Option>()
    private var tdLandTypes   = listOf<TranslatedDropdown.Option>()
    private var tdOnfield     = listOf<TranslatedDropdown.Option>()
    private var tdCropCond    = listOf<TranslatedDropdown.Option>()

    private fun setupSpinners() {
        val ctx = requireContext()
        tdSeasons    = TranslatedDropdown.seasons(ctx)
        tdSchemes    = TranslatedDropdown.schemes(ctx)
        tdCrops      = TranslatedDropdown.crops(ctx)
        tdInsurance  = TranslatedDropdown.insuranceUnits(ctx)
        tdCropStages = TranslatedDropdown.cropStages(ctx)
        tdIrrigation = TranslatedDropdown.irrigationTypes(ctx)
        tdLandTypes  = TranslatedDropdown.landTypes(ctx)
        tdOnfield    = TranslatedDropdown.onfieldConditions(ctx)
        tdCropCond   = TranslatedDropdown.onfieldConditions(ctx)

        setSpinner(spYear,              listOf("Select Year")            + AppConfig.YEARS)
        setSpinner(spSeason,            listOf(getString(R.string.hint_select)) + tdLabels(tdSeasons))
        setSpinner(spScheme,            listOf(getString(R.string.hint_select)) + tdLabels(tdSchemes))
        setSpinner(spCropName,          listOf(getString(R.string.hint_select)) + tdLabels(tdCrops))
        setSpinner(spInsuranceUnit,     listOf(getString(R.string.hint_select)) + tdLabels(tdInsurance))
        setSpinner(spCropStage,         listOf(getString(R.string.hint_select)) + tdLabels(tdCropStages))
        setSpinner(spIrrigationType,    listOf(getString(R.string.hint_select)) + tdLabels(tdIrrigation))
        setSpinner(spLandType,          listOf(getString(R.string.hint_select)) + tdLabels(tdLandTypes))
        setSpinner(spOnfieldCondition,  listOf(getString(R.string.hint_select)) + tdLabels(tdOnfield))
        setSpinner(spPlotSize,          listOf("Select Plot Size")       + AppConfig.CCE_PLOT_SIZES)
        setSpinner(spThreshingMethod,   listOf("Select Method")          + AppConfig.CCE_THRESHING_METHODS)
        setSpinner(spCropConditionPlot, listOf(getString(R.string.hint_select)) + tdLabels(tdCropCond))
        setSpinner(spSamplingMethod,    listOf("Select Sampling Method") + AppConfig.CCE_SAMPLING_METHOD)
        setSpinner(spWitnessType,       listOf("Select Witness Type")    + AppConfig.CCE_WITNESS_TYPES)
        setSpinner(spState,    listOf("Select State"))
        setSpinner(spDistrict, listOf("Select District"))
        setSpinner(spTehsil,   listOf("Select Tehsil"))

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
                            setSpinner(spDistrict, listOf("Select District") + districts)
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
                            setSpinner(spTehsil, listOf("Select Tehsil") + subDistricts)
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
        listOf(etSowingDate, etExpectedHarvest, etHarvestingDate, etCceDate, etThreshingDate).forEach { et ->
            et.isFocusable = false
            et.setOnClickListener {
                val cal = Calendar.getInstance()
                DatePickerDialog(requireContext(), { _, y, m, d ->
                    et.setText("%04d-%02d-%02d".format(y, m + 1, d))
                    updateYieldEstimate()
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
    }

    private fun setupYieldWatchers() {
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = updateYieldEstimate()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        etDryGrain.addTextChangedListener(watcher)
        etMoisture.addTextChangedListener(watcher)
        etCustomPlotSize.addTextChangedListener(watcher)
    }

    private fun updateYieldEstimate() {
        val dryGrain = etDryGrain.text.toString().toDoubleOrNull()
        val plotSizeStr = spPlotSize.selectedItem?.toString() ?: ""
        val plotSqm = when {
            plotSizeStr.contains("100") -> 100.0
            plotSizeStr.contains("25")  -> 25.0
            plotSizeStr.contains("20")  -> 20.0
            plotSizeStr.contains("16")  -> 16.0
            plotSizeStr.contains("9")   -> 9.0
            plotSizeStr == "Custom"     -> etCustomPlotSize.text.toString().toDoubleOrNull()
            else -> null
        }
        if (dryGrain == null || plotSqm == null) {
            tvYieldEstimate.text = "Estimated Yield: —"
            return
        }
        val moisture = etMoisture.text.toString().toDoubleOrNull() ?: 0.0
        val rawYield = dryGrain / plotSqm * 10000
        val adjustedYield = if (moisture > 0 && moisture != 14.0) {
            rawYield * (100 - moisture) / (100 - 14.0)
        } else rawYield
        tvYieldEstimate.text = "Estimated Yield: %.2f kg/ha  (at 14%% moisture)".format(adjustedYield)
    }

    private fun loadStates() {
        lifecycleScope.launch {
            try {
                val res = withContext(Dispatchers.IO) { ApiClient.service.getStates() }
                if (res.isSuccessful) {
                    states = res.body() ?: emptyList()
                    val a = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item,
                        listOf("Select State") + states)
                    a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spState.adapter = a
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
                SurveySession.formData["capture_lat"] = coords.lat
                SurveySession.formData["capture_lon"] = coords.lon
            }
        }
    }

    private fun restoreFormData() {
        val fd = SurveySession.formData
        if (fd.isEmpty()) return

        etCceNumber.setText(fd["cce_number"]?.toString() ?: "")
        etCceDate.setText(fd["cce_date"]?.toString() ?: "")
        etExperimentId.setText(fd["experiment_id"]?.toString() ?: "")
        etFarmerName.setText(fd["farmer_name"]?.toString() ?: "")
        etFarmerMobile.setText(fd["farmer_mobile"]?.toString() ?: "")
        etFarmerAadhaarLast4.setText(fd["farmer_aadhaar_last4"]?.toString() ?: "")
        etFarmerAppNo.setText(fd["farmer_insurance_app_no"]?.toString() ?: "")
        etKhasraNo.setText(fd["khasra_no"]?.toString() ?: "")
        etVillage.setText(fd["village"]?.toString() ?: "")
        etRevenueCircle.setText(fd["revenue_circle"]?.toString() ?: "")
        etGramPanchayat.setText(fd["gram_panchayat"]?.toString() ?: "")
        etCropVariety.setText(fd["crop_variety"]?.toString() ?: "")
        etSowingDate.setText(fd["sowing_date"]?.toString() ?: "")
        etExpectedHarvest.setText(fd["expected_harvest_date"]?.toString() ?: "")
        etCustomPlotSize.setText(fd["cce_plot_custom"]?.toString() ?: "")
        etHarvestingDate.setText(fd["harvesting_date"]?.toString() ?: "")
        etThreshingDate.setText(fd["threshing_date"]?.toString() ?: "")
        etFreshBiomass.setText(fd["fresh_biomass_weight"]?.toString() ?: "")
        etDryBiomass.setText(fd["dry_biomass_weight"]?.toString() ?: "")
        etFreshGrain.setText(fd["fresh_grain_weight"]?.toString() ?: "")
        etDryGrain.setText(fd["dry_grain_weight"]?.toString() ?: "")
        etMoisture.setText(fd["moisture_content"]?.toString() ?: "")
        etIcRepName.setText(fd["ic_representative_name"]?.toString() ?: "")
        etIcRepMobile.setText(fd["ic_representative_mobile"]?.toString() ?: "")
        etRevenueOfficer.setText(fd["revenue_officer_name"]?.toString() ?: "")
        cbFarmerPresent.isChecked = fd["farmer_present"] as? Boolean ?: false
        etRemarks.setText(fd["remarks"]?.toString() ?: "")
        etOtherScheme.setText(fd["others_scheme"]?.toString() ?: "")

        restoreSpinner(spYear,              fd["year"]?.toString())
        tdRestore(spSeason, tdSeasons,            fd["season"]?.toString())
        tdRestore(spScheme, tdSchemes,            fd["scheme"]?.toString())
        tdRestore(spCropName, tdCrops,          fd["crop_name"]?.toString())
        tdRestore(spInsuranceUnit, tdInsurance,     fd["insurance_unit"]?.toString())
        tdRestore(spCropStage, tdCropStages,         fd["crop_stage"]?.toString())
        tdRestore(spIrrigationType, tdIrrigation,    fd["irrigation_type"]?.toString())
        tdRestore(spLandType, tdLandTypes,          fd["land_type"]?.toString())
        tdRestore(spOnfieldCondition, tdOnfield,  fd["onfield_condition"]?.toString())
        restoreSpinner(spPlotSize,          fd["cce_plot_size"]?.toString())
        restoreSpinner(spThreshingMethod,   fd["threshing_method"]?.toString())
        tdRestore(spCropConditionPlot, tdCropCond, fd["crop_condition_plot"]?.toString())
        restoreSpinner(spSamplingMethod,    fd["sampling_method"]?.toString())
        restoreSpinner(spWitnessType,       fd["witness_type"]?.toString())

        updateYieldEstimate()

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
                val resD = withContext(Dispatchers.IO) { ApiClient.service.getDistricts(savedState) }
                if (resD.isSuccessful) {
                    districts = resD.body() ?: emptyList()
                    setSpinner(spDistrict, listOf("Select District") + districts)
                    restoreSpinner(spDistrict, savedDistrict)
                    SurveySession.formData["district"] = savedDistrict
                    if (savedTehsil != null) {
                        val resT = withContext(Dispatchers.IO) {
                            ApiClient.service.getSubDistricts(savedState, savedDistrict)
                        }
                        if (resT.isSuccessful) {
                            subDistricts = resT.body() ?: emptyList()
                            setSpinner(spTehsil, listOf("Select Tehsil") + subDistricts)
                            restoreSpinner(spTehsil, savedTehsil)
                            SurveySession.formData["tehsil"] = savedTehsil
                        }
                    }
                }
            } catch (e: Exception) {} finally {
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
        if (etFarmerName.text.isBlank()) { etFarmerName.error = "Required"; expandAndScrollTo(3, etFarmerName); return false }
        if (etFarmerMobile.text.length < 10) { etFarmerMobile.error = "Valid 10-digit mobile required"; expandAndScrollTo(3, etFarmerMobile); return false }
        if (spCropName.selectedItemPosition == 0) { expandAndScrollTo(4, spCropName); return false }
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
        val farmerName   = etFarmerName.text.toString().trim()
        val farmerMobile = etFarmerMobile.text.toString().trim()

        val dryGrain    = etDryGrain.text.toString().toDoubleOrNull()
        val moisture    = etMoisture.text.toString().toDoubleOrNull() ?: 0.0
        val plotSizeStr = spPlotSize.selectedItem?.toString() ?: ""
        val plotSqm = when {
            plotSizeStr.contains("100") -> 100.0
            plotSizeStr.contains("25")  -> 25.0
            plotSizeStr.contains("20")  -> 20.0
            plotSizeStr.contains("16")  -> 16.0
            plotSizeStr.contains("9")   -> 9.0
            plotSizeStr == "Custom"     -> etCustomPlotSize.text.toString().toDoubleOrNull()
            else -> null
        }
        val yieldKgHa = if (dryGrain != null && plotSqm != null) {
            val raw = dryGrain / plotSqm * 10000
            if (moisture > 0 && moisture != 14.0) raw * (100 - moisture) / (100 - 14.0) else raw
        } else null

        return mapOf(
            "year"                      to spYear.selectedItem?.toString()?.takeIf { it != "Select Year" },
            "season"                    to tdCode(tdSeasons, spSeason.selectedItemPosition - 1),
            "scheme"                    to tdCode(tdSchemes, spScheme.selectedItemPosition - 1),
            "others_scheme"             to etOtherScheme.text.toString().takeIf { it.isNotEmpty() },
            "cce_number"                to etCceNumber.text.toString().takeIf { it.isNotEmpty() },
            "cce_date"                  to etCceDate.text.toString().takeIf { it.isNotEmpty() },
            "experiment_id"             to etExperimentId.text.toString().takeIf { it.isNotEmpty() },
            "state"                     to SurveySession.formData["state"],
            "district"                  to SurveySession.formData["district"],
            "tehsil"                    to (spTehsil.selectedItem?.toString()?.takeIf { it != "Select Tehsil" } ?: SurveySession.formData["tehsil"]),
            "revenue_circle"            to etRevenueCircle.text.toString().takeIf { it.isNotEmpty() },
            "gram_panchayat"            to etGramPanchayat.text.toString().takeIf { it.isNotEmpty() },
            "village"                   to etVillage.text.toString().trim(),
            "insurance_unit"            to tdCode(tdInsurance, spInsuranceUnit.selectedItemPosition - 1),
            "farmer_name"               to farmerName.takeIf { it.isNotEmpty() },
            "farmer_mobile"             to farmerMobile.takeIf { it.isNotEmpty() },
            "farmer_aadhaar_last4"      to etFarmerAadhaarLast4.text.toString().takeIf { it.isNotEmpty() },
            "farmer_insurance_app_no"   to etFarmerAppNo.text.toString().takeIf { it.isNotEmpty() },
            "farmer_verified"           to (SurveySession.formData["farmer_verified"]?.toString() ?: "skipped"),
            "farmer_phone_verified"     to (SurveySession.formData["farmer_phone"]?.toString() ?: ""),
            "khasra_no"                 to etKhasraNo.text.toString().takeIf { it.isNotEmpty() },
            "crop_name"                 to tdCode(tdCrops, spCropName.selectedItemPosition - 1),
            "crop_variety"              to etCropVariety.text.toString().takeIf { it.isNotEmpty() },
            "sowing_date"               to etSowingDate.text.toString().takeIf { it.isNotEmpty() },
            "expected_harvest_date"     to etExpectedHarvest.text.toString().takeIf { it.isNotEmpty() },
            "crop_stage"                to tdCode(tdCropStages, spCropStage.selectedItemPosition - 1),
            "irrigation_type"           to tdCode(tdIrrigation, spIrrigationType.selectedItemPosition - 1),
            "land_type"                 to tdCode(tdLandTypes, spLandType.selectedItemPosition - 1),
            "field_area_polygon"        to etFieldAreaPolygon.text.toString().toDoubleOrNull(),
            "cce_plot_size"             to spPlotSize.selectedItem?.toString()?.takeIf { it != "Select Plot Size" },
            "cce_plot_custom"           to etCustomPlotSize.text.toString().toDoubleOrNull(),
            "sampling_method"           to spSamplingMethod.selectedItem?.toString()?.takeIf { !it.contains("Select") },
            "crop_condition_plot"       to spCropConditionPlot.selectedItem?.toString()?.takeIf { !it.contains("Crop Condition") },
            "harvesting_date"           to etHarvestingDate.text.toString().takeIf { it.isNotEmpty() },
            "threshing_method"          to spThreshingMethod.selectedItem?.toString()?.takeIf { it != "Select Method" },
            "threshing_date"            to etThreshingDate.text.toString().takeIf { it.isNotEmpty() },
            "fresh_biomass_weight"      to etFreshBiomass.text.toString().toDoubleOrNull(),
            "dry_biomass_weight"        to etDryBiomass.text.toString().toDoubleOrNull(),
            "fresh_grain_weight"        to etFreshGrain.text.toString().toDoubleOrNull(),
            "dry_grain_weight"          to etDryGrain.text.toString().toDoubleOrNull(),
            "moisture_content"          to etMoisture.text.toString().toDoubleOrNull(),
            "yield_kg_per_ha"           to yieldKgHa,
            "witness_type"              to spWitnessType.selectedItem?.toString()?.takeIf { !it.contains("Select") },
            "ic_representative_name"    to etIcRepName.text.toString().takeIf { it.isNotEmpty() },
            "ic_representative_mobile"  to etIcRepMobile.text.toString().takeIf { it.isNotEmpty() },
            "revenue_officer_name"      to etRevenueOfficer.text.toString().takeIf { it.isNotEmpty() },
            "farmer_present"            to cbFarmerPresent.isChecked,
            "onfield_condition"         to tdCode(tdOnfield, spOnfieldCondition.selectedItemPosition - 1),
            "remarks"                   to etRemarks.text.toString().takeIf { it.isNotEmpty() },
            "capture_lat"               to SurveySession.formData["capture_lat"],
            "capture_lon"               to SurveySession.formData["capture_lon"],
        )
    }

    fun collectFormData(): Map<String, Any?> {
        if (!validateRequiredFields()) return emptyMap()
        val farmerName   = etFarmerName.text.toString().trim()
        val farmerMobile = etFarmerMobile.text.toString().trim()

        val dryGrain    = etDryGrain.text.toString().toDoubleOrNull()
        val moisture    = etMoisture.text.toString().toDoubleOrNull() ?: 0.0
        val plotSizeStr = spPlotSize.selectedItem?.toString() ?: ""
        val plotSqm = when {
            plotSizeStr.contains("100") -> 100.0
            plotSizeStr.contains("25")  -> 25.0
            plotSizeStr.contains("20")  -> 20.0
            plotSizeStr.contains("16")  -> 16.0
            plotSizeStr.contains("9")   -> 9.0
            plotSizeStr == "Custom"     -> etCustomPlotSize.text.toString().toDoubleOrNull()
            else -> null
        }
        val yieldKgHa = if (dryGrain != null && plotSqm != null) {
            val raw = dryGrain / plotSqm * 10000
            if (moisture > 0 && moisture != 14.0) raw * (100 - moisture) / (100 - 14.0) else raw
        } else null

        return mapOf(
            "year"                      to spYear.selectedItem?.toString()?.takeIf { it != "Select Year" },
            "season"                    to tdCode(tdSeasons, spSeason.selectedItemPosition - 1),
            "scheme"                    to tdCode(tdSchemes, spScheme.selectedItemPosition - 1),
            "others_scheme"             to etOtherScheme.text.toString().takeIf { it.isNotEmpty() },
            "cce_number"                to etCceNumber.text.toString().takeIf { it.isNotEmpty() },
            "cce_date"                  to etCceDate.text.toString().takeIf { it.isNotEmpty() },
            "experiment_id"             to etExperimentId.text.toString().takeIf { it.isNotEmpty() },
            "state"                     to SurveySession.formData["state"],
            "district"                  to SurveySession.formData["district"],
            "tehsil"                    to (spTehsil.selectedItem?.toString()?.takeIf { it != "Select Tehsil" } ?: SurveySession.formData["tehsil"]),
            "revenue_circle"            to etRevenueCircle.text.toString().takeIf { it.isNotEmpty() },
            "gram_panchayat"            to etGramPanchayat.text.toString().takeIf { it.isNotEmpty() },
            "village"                   to etVillage.text.toString().trim(),
            "insurance_unit"            to tdCode(tdInsurance, spInsuranceUnit.selectedItemPosition - 1),
            "farmer_name"               to farmerName,
            "farmer_mobile"             to farmerMobile,
            "farmer_aadhaar_last4"      to etFarmerAadhaarLast4.text.toString().takeIf { it.isNotEmpty() },
            "farmer_insurance_app_no"   to etFarmerAppNo.text.toString().takeIf { it.isNotEmpty() },
            "farmer_verified"          to (SurveySession.formData["farmer_verified"]?.toString() ?: "skipped"),
            "farmer_phone_verified"    to (SurveySession.formData["farmer_phone"]?.toString() ?: ""),
            "khasra_no"                 to etKhasraNo.text.toString().takeIf { it.isNotEmpty() },
            "crop_name"                 to tdCode(tdCrops, spCropName.selectedItemPosition - 1),
            "crop_variety"              to etCropVariety.text.toString().takeIf { it.isNotEmpty() },
            "sowing_date"               to etSowingDate.text.toString().takeIf { it.isNotEmpty() },
            "expected_harvest_date"     to etExpectedHarvest.text.toString().takeIf { it.isNotEmpty() },
            "crop_stage"                to tdCode(tdCropStages, spCropStage.selectedItemPosition - 1),
            "irrigation_type"           to tdCode(tdIrrigation, spIrrigationType.selectedItemPosition - 1),
            "land_type"                 to tdCode(tdLandTypes, spLandType.selectedItemPosition - 1),
            "field_area_polygon"        to etFieldAreaPolygon.text.toString().toDoubleOrNull(),
            "cce_plot_size"             to spPlotSize.selectedItem?.toString()?.takeIf { it != "Select Plot Size" },
            "cce_plot_custom"           to etCustomPlotSize.text.toString().toDoubleOrNull(),
            "sampling_method"           to spSamplingMethod.selectedItem?.toString()?.takeIf { !it.contains("Select") },
            "crop_condition_plot"       to spCropConditionPlot.selectedItem?.toString()?.takeIf { !it.contains("Crop Condition") },
            "harvesting_date"           to etHarvestingDate.text.toString().takeIf { it.isNotEmpty() },
            "threshing_method"          to spThreshingMethod.selectedItem?.toString()?.takeIf { it != "Select Method" },
            "threshing_date"            to etThreshingDate.text.toString().takeIf { it.isNotEmpty() },
            "fresh_biomass_weight"      to etFreshBiomass.text.toString().toDoubleOrNull(),
            "dry_biomass_weight"        to etDryBiomass.text.toString().toDoubleOrNull(),
            "fresh_grain_weight"        to etFreshGrain.text.toString().toDoubleOrNull(),
            "dry_grain_weight"          to etDryGrain.text.toString().toDoubleOrNull(),
            "moisture_content"          to etMoisture.text.toString().toDoubleOrNull(),
            "yield_kg_per_ha"           to yieldKgHa,
            "witness_type"              to spWitnessType.selectedItem?.toString()?.takeIf { !it.contains("Select") },
            "ic_representative_name"    to etIcRepName.text.toString().takeIf { it.isNotEmpty() },
            "ic_representative_mobile"  to etIcRepMobile.text.toString().takeIf { it.isNotEmpty() },
            "revenue_officer_name"      to etRevenueOfficer.text.toString().takeIf { it.isNotEmpty() },
            "farmer_present"            to cbFarmerPresent.isChecked,
            "onfield_condition"         to tdCode(tdOnfield, spOnfieldCondition.selectedItemPosition - 1),
            "remarks"                   to etRemarks.text.toString().takeIf { it.isNotEmpty() },
            "capture_lat"               to SurveySession.formData["capture_lat"],
            "capture_lon"               to SurveySession.formData["capture_lon"],
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