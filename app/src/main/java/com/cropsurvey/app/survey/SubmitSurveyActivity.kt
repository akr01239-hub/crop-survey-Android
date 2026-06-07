package com.cropsurvey.app.survey

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.cropsurvey.app.BaseActivity
import androidx.lifecycle.lifecycleScope
import com.cropsurvey.app.R
import com.cropsurvey.app.dashboard.DashboardActivity
import com.cropsurvey.app.network.ApiClient
import com.cropsurvey.app.queue.QueueManager
import com.cropsurvey.app.utils.SurveySession
import kotlinx.coroutines.launch

class SubmitSurveyActivity : BaseActivity() {

    private lateinit var tvSurveyType: TextView
    private lateinit var tvCaseId: TextView
    private lateinit var tvFarmerName: TextView
    private lateinit var tvPhotoCount: TextView
    private lateinit var tvFormSummary: TextView
    private lateinit var btnSubmit: Button
    private lateinit var btnBack: ImageButton
    private lateinit var progressBar: ProgressBar

    private lateinit var surveyType: String
    private lateinit var surveyId: String
    private var chmVisitNumber: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_submit_survey)

        surveyType = intent.getStringExtra("survey_type") ?: "CLS"
        surveyId = intent.getStringExtra("survey_id") ?: ""
        chmVisitNumber = intent.getIntExtra("chm_visit_number", 0)

        bindViews()
        showSummary()
        setupButtons()
    }

    private fun bindViews() {
        tvSurveyType  = findViewById(R.id.tv_survey_type)
        tvCaseId      = findViewById(R.id.tv_case_id)
        tvFarmerName  = findViewById(R.id.tv_farmer_name)
        tvPhotoCount  = findViewById(R.id.tv_photo_count)
        tvFormSummary = findViewById(R.id.tv_form_summary)
        btnSubmit     = findViewById(R.id.btn_submit)
        btnBack       = findViewById(R.id.btn_back)
        progressBar   = findViewById(R.id.progress_bar)
    }

    private fun showSummary() {
        val fd = SurveySession.formData
        val (captured, total) = SurveySession.getPhotoCount(surveyType)

        tvSurveyType.text = if (chmVisitNumber > 0) "$surveyType — Visit $chmVisitNumber" else surveyType
        tvCaseId.text     = surveyId.take(12) + "..."
        tvFarmerName.text = fd["farmer_name"]?.toString() ?: "—"
        tvPhotoCount.text = "$captured / $total photos"

        val summary = buildString {
            append("Crop: ${fd["crop_name"] ?: "—"}\n")
            append("State: ${fd["state"] ?: "—"}, ${fd["district"] ?: "—"}\n")
            append("Village: ${fd["village"] ?: "—"}\n")
            append("Season: ${fd["season"] ?: "—"}, ${fd["year"] ?: "—"}")
        }
        tvFormSummary.text = summary
    }

    private fun setupButtons() {
        btnBack.setOnClickListener { finish() }

        btnSubmit.setOnClickListener {
            progressBar.visibility = View.VISIBLE
            btnSubmit.isEnabled = false

            lifecycleScope.launch {
                try {
                    val res = ApiClient.service.submitSurvey(surveyId)
                    if (res.isSuccessful) {
                        // Server confirmed submission — clear any stale queue entry
                        QueueManager.getItems(this@SubmitSurveyActivity)
                            .filter { it.surveyId == surveyId && it.type == "submit" }
                            .forEach { QueueManager.removeItem(this@SubmitSurveyActivity, it.id) }

                        Toast.makeText(this@SubmitSurveyActivity, "✅ Survey submitted successfully!", Toast.LENGTH_LONG).show()
                        SurveySession.clearPolygonOnSubmit()
                        SurveySession.clearPhotoFiles(applicationContext)  // delete photo files from filesDir now that survey is submitted
                        SurveySession.reset()
                        // Track submission count — auto guide runs for first 2 surveys
                        AiGuideOverlay.onSurveySubmitted(this@SubmitSurveyActivity)
                        val intent = Intent(this@SubmitSurveyActivity, DashboardActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                        startActivity(intent)
                        finish()
                    } else {
                        // Server rejected (4xx/5xx) — queue for retry so it doesn't stay draft silently
                        QueueManager.enqueueSubmit(this@SubmitSurveyActivity, surveyId)
                        Toast.makeText(
                            this@SubmitSurveyActivity,
                            "⚠️ Submission queued — will retry when connection improves.",
                            Toast.LENGTH_LONG
                        ).show()
                        SurveySession.reset()
                        val intent = Intent(this@SubmitSurveyActivity, DashboardActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                        startActivity(intent)
                        finish()
                    }
                } catch (e: Exception) {
                    // Network error — queue submit for offline retry
                    QueueManager.enqueueSubmit(this@SubmitSurveyActivity, surveyId)
                    Toast.makeText(
                        this@SubmitSurveyActivity,
                        "📶 Offline — submission queued. It will be sent automatically when you're back online.",
                        Toast.LENGTH_LONG
                    ).show()
                    SurveySession.reset()
                    val intent = Intent(this@SubmitSurveyActivity, DashboardActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    startActivity(intent)
                    finish()
                } finally {
                    progressBar.visibility = View.GONE
                }
            }
        }
    }
}