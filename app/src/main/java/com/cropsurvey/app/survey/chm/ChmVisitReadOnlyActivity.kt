package com.cropsurvey.app.survey.chm

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cropsurvey.app.R
import com.cropsurvey.app.network.ApiClient
import kotlinx.coroutines.launch

/**
 * Read-only display of an approved CHM visit.
 * All fields are shown as TextViews — no editing allowed.
 */
class ChmVisitReadOnlyActivity : AppCompatActivity() {

    private lateinit var tvVisitTitle: TextView
    private lateinit var tvCaseId: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvFormDetails: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnBack: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chm_visit_readonly)

        tvVisitTitle  = findViewById(R.id.tv_visit_title)
        tvCaseId      = findViewById(R.id.tv_case_id)
        tvStatus      = findViewById(R.id.tv_status)
        tvFormDetails = findViewById(R.id.tv_form_details)
        progressBar   = findViewById(R.id.progress_bar)
        btnBack       = findViewById(R.id.btn_back)

        val visitNumber = intent.getIntExtra("visit_number", 1)
        val surveyId    = intent.getStringExtra("survey_id") ?: ""
        val caseId      = intent.getStringExtra("case_id") ?: ""

        tvVisitTitle.text = "Visit $visitNumber — Approved ✓"
        tvCaseId.text     = "Case ID: $caseId"
        tvStatus.text     = "Status: Approved"

        btnBack.setOnClickListener { finish() }

        loadVisitData(surveyId)
    }

    private fun loadVisitData(surveyId: String) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val res = ApiClient.service.getSurvey(surveyId)
                if (res.isSuccessful) {
                    val survey = res.body()
                    progressBar.visibility = View.GONE
                    if (survey != null) {
                        displayFormData(survey.formData)
                    }
                } else {
                    progressBar.visibility = View.GONE
                    tvFormDetails.text = "Could not load visit data."
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                tvFormDetails.text = "Network error — unable to load."
            }
        }
    }

    private fun displayFormData(fd: Map<String, Any?>) {
        val displayKeys = listOf(
            "farmer_name"           to "Farmer Name",
            "farmer_mobile"         to "Mobile",
            "farmer_application_no" to "Application No.",
            "online_chm_id"         to "CHM ID",
            "year"                  to "Year",
            "season"                to "Season",
            "scheme"                to "Scheme",
            "state"                 to "State",
            "district"              to "District",
            "tehsil"                to "Tehsil",
            "village"               to "Village",
            "crop_name"             to "Crop",
            "crop_variety"          to "Variety",
            "crop_stage"            to "Crop Stage",
            "crop_health"           to "Crop Health",
            "sowing_date"           to "Sowing Date",
            "expected_harvest_date" to "Expected Harvest",
            "plant_count_per_sqm"   to "Plants/sqm",
            "row_spacing"           to "Row Spacing (cm)",
            "ridge_distance_cm"     to "Ridge Distance (cm)",
            "num_irrigations"       to "Irrigations",
            "fertilizer_kg_ha"      to "Fertilizer (kg/ha)",
            "pest_disease_incidents" to "Pest Incidents",
            "pest_disease_name"     to "Pest Name",
            "plant_weight_grams"    to "Plant Weight (g)",
            "tuber_count"           to "Tuber Count",
            "total_tuber_weight_grams" to "Tuber Weight (g)",
            "grain_only_weight"     to "Grain Weight (g)",
            "remarks"               to "Remarks"
        )

        val sb = StringBuilder()
        displayKeys.forEach { (key, label) ->
            val value = fd[key]
            if (value != null && value.toString().isNotEmpty()) {
                sb.append("$label: $value\n")
            }
        }

        tvFormDetails.text = if (sb.isEmpty()) "No data recorded." else sb.toString().trimEnd()
    }
}
