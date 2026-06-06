package com.cropsurvey.app.survey

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.cropsurvey.app.BaseActivity
import androidx.lifecycle.lifecycleScope
import com.cropsurvey.app.R
import com.cropsurvey.app.camera.PhotoSelectionActivity
import com.cropsurvey.app.models.UpdateSurveyRequest
import com.cropsurvey.app.network.ApiClient
import com.cropsurvey.app.queue.QueueManager
import com.cropsurvey.app.survey.cls.CLSFormFragment
import com.cropsurvey.app.survey.chm.CHMFormFragment
import com.cropsurvey.app.survey.cce.CCEFormFragment
import com.cropsurvey.app.utils.SurveySession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

class SurveyFormActivity : BaseActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvCaseId: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var btnSaveDraft: Button
    private lateinit var btnNext: Button
    private lateinit var progressBar: ProgressBar

    private lateinit var bannerResubmit: View
    private lateinit var tvBannerMsg: TextView

    private lateinit var surveyType: String
    private lateinit var surveyId: String
    private var isEditMode = false
    private var isResubmit = false
    private var farmerVerified = false
    private var farmerPhone = ""

    private var autoSaveJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_survey_form)

        surveyType     = intent.getStringExtra("survey_type") ?: "CLS"
        surveyId       = intent.getStringExtra("survey_id")   ?: ""
        isEditMode     = intent.getBooleanExtra("is_edit_mode", false)
        isResubmit     = intent.getBooleanExtra("is_resubmit", false)
        farmerVerified = intent.getBooleanExtra("farmer_verified", false)
        farmerPhone    = intent.getStringExtra("farmer_phone") ?: ""

        bindViews()
        setupFarmerBanner()
        // Farmer data is already in SurveySession (set by FarmerVerificationActivity)
        // Use intent values as fallback for direct launches (e.g. edit draft)
        if (farmerVerified && farmerPhone.isNotEmpty()) {
            SurveySession.formData["farmer_phone"]    = farmerPhone
            SurveySession.formData["farmer_verified"] = "yes"
        } else if (SurveySession.formData["farmer_verified"]?.toString() == "yes") {
            // Already set by FarmerVerificationActivity — sync local vars
            farmerVerified = true
            farmerPhone = SurveySession.formData["farmer_phone"]?.toString() ?: ""
        }
        setupHeader()
        loadFormFragment()
        setupResubmitBanner()
        startAutoSave()
    }

    private lateinit var bannerFarmerVerified: View
    private lateinit var tvFarmerVerifiedPhone: TextView

    private fun bindViews() {
        tvTitle              = findViewById(R.id.tv_title)
        tvCaseId             = findViewById(R.id.tv_case_id)
        btnBack              = findViewById(R.id.btn_back)
        btnSaveDraft         = findViewById(R.id.btn_save_draft)
        btnNext              = findViewById(R.id.btn_next)
        progressBar          = findViewById(R.id.progress_bar)
        bannerResubmit       = findViewById(R.id.banner_resubmit)
        tvBannerMsg          = findViewById(R.id.tv_banner_msg)
        bannerFarmerVerified = findViewById(R.id.banner_farmer_verified)
        tvFarmerVerifiedPhone = findViewById(R.id.tv_farmer_verified_phone)
    }

    private fun setupFarmerBanner() {
        if (farmerVerified && farmerPhone.isNotEmpty()) {
            bannerFarmerVerified.visibility = View.VISIBLE
            tvFarmerVerifiedPhone.text = "✅ Farmer Verified: +91-$farmerPhone"
        } else {
            bannerFarmerVerified.visibility = View.GONE
        }
    }

    private fun setupHeader() {
        val baseTitle = when (surveyType) {
            "CLS" -> "Crop Loss Survey"
            "CHM" -> "Crop Health Monitoring"
            "CCE" -> "Crop Cutting Experiment"
            else  -> "Survey Form"
        }
        tvTitle.text  = if (isResubmit) "Re-edit: $baseTitle" else baseTitle
        tvCaseId.text = "Case: ${surveyId.take(8)}..."

        btnBack.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Unsaved Changes")
                .setMessage("Save draft before leaving?")
                .setPositiveButton("Save Draft") { _, _ -> saveDraft { finish() } }
                .setNegativeButton("Discard") { _, _ -> finish() }
                .setNeutralButton("Cancel", null)
                .show()
        }

        btnSaveDraft.setOnClickListener { saveDraft(null) }

        // FIX: correct label — always goes to Photos, not directly to Submit
        btnNext.text = if (isResubmit) "Save & Go to Photos →" else "Next →"

        btnNext.setOnClickListener {
            val fragment = supportFragmentManager.findFragmentById(R.id.form_container)
            // First validate required * fields — scrolls to first missing field
            val valid = when (fragment) {
                is CLSFormFragment -> fragment.validateRequiredFields()
                is CHMFormFragment -> fragment.validateRequiredFields()
                is CCEFormFragment -> fragment.validateRequiredFields()
                else -> true
            }
            if (!valid) return@setOnClickListener

            val data = when (fragment) {
                is CLSFormFragment -> fragment.collectFormData()
                is CHMFormFragment -> fragment.collectFormData()
                is CCEFormFragment -> fragment.collectFormData()
                else -> emptyMap()
            }

            if (data.isEmpty()) return@setOnClickListener

            val fullData = data.toMutableMap()
            if (farmerVerified && farmerPhone.isNotEmpty()) {
                fullData["farmer_verified"] = true
                fullData["farmer_phone"]    = farmerPhone
            }
            SurveySession.updateFormData(fullData)
            saveDraft {
                val intent = Intent(this, PhotoSelectionActivity::class.java)
                intent.putExtra("survey_type", surveyType)
                intent.putExtra("survey_id", surveyId)
                intent.putExtra("is_resubmit", isResubmit)
                startActivity(intent)
            }
        }
    }

    private fun setupResubmitBanner() {
        if (isResubmit) {
            bannerResubmit.visibility = View.VISIBLE
            tvBannerMsg.text = "⚠ This survey was rejected. Update the details below and resubmit."
        } else if (isEditMode) {
            bannerResubmit.visibility = View.VISIBLE
            tvBannerMsg.text = "✏ Editing draft — make changes and tap Save Draft or Next."
        } else {
            bannerResubmit.visibility = View.GONE
        }
    }

    private fun loadFormFragment() {
        val fragment = when (surveyType) {
            "CLS" -> CLSFormFragment()
            "CHM" -> CHMFormFragment()
            "CCE" -> CCEFormFragment()
            else  -> CLSFormFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.form_container, fragment)
            .commit()
    }

    private fun saveDraft(onSuccess: (() -> Unit)?) {
        progressBar.visibility = View.VISIBLE
        btnSaveDraft.isEnabled = false
        lifecycleScope.launch {
            try {
                val res = ApiClient.service.updateSurvey(
                    surveyId,
                    UpdateSurveyRequest(SurveySession.formData, SurveySession.polygonGeoJson)
                )
                if (res.isSuccessful) {
                    Toast.makeText(this@SurveyFormActivity, "Draft saved", Toast.LENGTH_SHORT).show()
                    onSuccess?.invoke()
                } else {
                    QueueManager.enqueueFormUpdate(this@SurveyFormActivity, surveyId, SurveySession.formData)
                    Toast.makeText(this@SurveyFormActivity, "Saved locally (offline)", Toast.LENGTH_SHORT).show()
                    onSuccess?.invoke()
                }
            } catch (e: Exception) {
                QueueManager.enqueueFormUpdate(this@SurveyFormActivity, surveyId, SurveySession.formData)
                Toast.makeText(this@SurveyFormActivity, "Saved locally (offline)", Toast.LENGTH_SHORT).show()
                onSuccess?.invoke()
            } finally {
                progressBar.visibility = View.GONE
                btnSaveDraft.isEnabled = true
            }
        }
    }

    private fun startAutoSave() {
        autoSaveJob = lifecycleScope.launch {
            while (true) {
                delay(com.cropsurvey.app.config.AppConfig.AUTO_SAVE_INTERVAL_MS)
                try {
                    ApiClient.service.updateSurvey(
                        surveyId,
                        UpdateSurveyRequest(SurveySession.formData, SurveySession.polygonGeoJson)
                    )
                } catch (e: Exception) { /* offline — skip */ }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        autoSaveJob?.cancel()
    }
}