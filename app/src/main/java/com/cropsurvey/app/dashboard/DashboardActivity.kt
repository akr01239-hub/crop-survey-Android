package com.cropsurvey.app.dashboard

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.cropsurvey.app.BaseActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cropsurvey.app.R
import com.cropsurvey.app.auth.LoginActivity
import com.cropsurvey.app.map.PolygonMapActivity
import com.cropsurvey.app.survey.FarmerVerificationActivity
import com.cropsurvey.app.models.CapturedPhoto
import com.cropsurvey.app.models.Survey
import com.cropsurvey.app.network.ApiClient
import com.cropsurvey.app.queue.QueueManager
import com.cropsurvey.app.survey.SurveyTabsActivity
import com.cropsurvey.app.survey.SubmitSurveyActivity
import com.cropsurvey.app.utils.SessionManager
import com.cropsurvey.app.utils.MockLocationDetector
import com.cropsurvey.app.utils.SurveySession
import com.cropsurvey.app.guide.AiGuideOverlay
import com.cropsurvey.app.guide.OnboardingGuideActivity
import android.animation.ObjectAnimator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import kotlinx.coroutines.launch
import java.util.Calendar

class DashboardActivity : BaseActivity() {

    private lateinit var tvGreeting: TextView
    private lateinit var tvUserName: TextView
    private lateinit var btnCLS: View
    private lateinit var btnCHM: View
    private lateinit var btnCCE: View
    private lateinit var rvRecentSurveys: RecyclerView
    private lateinit var tvNoSurveys: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutLoading: View
    private lateinit var tvLoadingStatus: TextView
    private lateinit var dot1: View
    private lateinit var dot2: View
    private lateinit var dot3: View
    private var dotAnimator: AnimatorSet? = null
    private lateinit var btnLogout: ImageButton
    private lateinit var btnQueue: ImageButton
    private lateinit var tvQueueBadge: TextView
    private lateinit var btnAiGuide: ImageButton

    // FIX 3: Stat cards — all-time totals (not month-filtered)
    private lateinit var tvStatDraft: TextView
    private lateinit var tvStatSubmitted: TextView
    private lateinit var tvStatApproved: TextView
    private lateinit var tvStatRejected: TextView

    // FIX 3: Tab labels — show count badge e.g. "Draft (3)"
    private lateinit var tabDraft: TextView
    private lateinit var tabSubmitted: TextView
    private lateinit var tabApproved: TextView
    private lateinit var tabRejected: TextView

    private lateinit var surveysAdapter: RecentSurveysAdapter
    private var currentTab = "draft"
    private var allSurveys: List<Survey> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        bindViews()
        setupHeader()
        setupSurveyCards()
        setupRecyclerView()
        setupTabs()
        loadRecentSurveys()
        updateQueueBadge()

        // Show onboarding guide once on first login
        OnboardingGuideActivity.showIfNeeded(this)

        // Show AI guide step for dashboard
        AiGuideOverlay.show(this, AiGuideOverlay.Step.DASHBOARD)
    }

    override fun onResume() {
        super.onResume()

        // ── Mock location check on every resume ───────────────────────────────
        if (MockLocationDetector.isMockLocation(this)) {
            MockLocationDetector.showCrashDialogAndKill(this)
            return
        }

        // ── Device lock check — kick out if session is from a different device ─
        if (!SessionManager.isCurrentDevice(this)) {
            SessionManager.clearSession()
            androidx.appcompat.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("🔒 Session Expired on This Device")
                .setMessage(
                    "This account has been logged in on another device.\n\n" +
                            "For security and data integrity, only one device can use an account at a time.\n\n" +
                            "Contact Admin: Akshay Rai to unlock your account."
                )
                .setCancelable(false)
                .setPositiveButton("OK") { _, _ ->
                    val intent = android.content.Intent(this, com.cropsurvey.app.auth.LoginActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                .create()
                .also { it.setCanceledOnTouchOutside(false) }
                .show()
            return
        }

        updateQueueBadge()
        loadRecentSurveys()
        QueueManager.flush(this)
    }

    private fun bindViews() {
        tvGreeting      = findViewById(R.id.tv_greeting)
        tvUserName      = findViewById(R.id.tv_user_name)
        btnCLS          = findViewById(R.id.btn_cls)
        btnCHM          = findViewById(R.id.btn_chm)
        btnCCE          = findViewById(R.id.btn_cce)
        rvRecentSurveys = findViewById(R.id.rv_recent_surveys)
        tvNoSurveys     = findViewById(R.id.tv_no_surveys)
        progressBar     = findViewById(R.id.progress_bar)
        layoutLoading   = findViewById(R.id.layout_loading)
        tvLoadingStatus = findViewById(R.id.tv_loading_status)
        dot1            = findViewById(R.id.dot1)
        dot2            = findViewById(R.id.dot2)
        dot3            = findViewById(R.id.dot3)
        btnLogout       = findViewById(R.id.btn_logout)
        btnQueue        = findViewById(R.id.btn_queue)
        tvQueueBadge    = findViewById(R.id.tv_queue_badge)
        btnAiGuide      = findViewById(R.id.btn_ai_guide)
        tvStatDraft     = findViewById(R.id.tv_stat_draft)
        tvStatSubmitted = findViewById(R.id.tv_stat_submitted)
        tvStatApproved  = findViewById(R.id.tv_stat_approved)
        tvStatRejected  = findViewById(R.id.tv_stat_rejected)
        tabDraft        = findViewById(R.id.tab_draft)
        tabSubmitted    = findViewById(R.id.tab_submitted)
        tabApproved     = findViewById(R.id.tab_approved)
        tabRejected     = findViewById(R.id.tab_rejected)
    }

    private fun setupHeader() {
        val user = SessionManager.getUser()
        tvGreeting.text = getGreeting()
        tvUserName.text = user?.name?.ifBlank { "Surveyor" } ?: "Surveyor"

        if (user != null) {
            SurveySession.userId = user.id
            // Always try to get fresh employee ID from API — don't rely on stale cache
            refreshEmployeeId(user)
        }

        btnAiGuide.setOnClickListener {
            AiGuideOverlay.showGuideMenu(this)
        }

        btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.logout_confirm_title))
                .setMessage(getString(R.string.logout_confirm_msg))
                .setPositiveButton(getString(R.string.logout)) { _, _ ->
                    SessionManager.clearSession()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnQueue.setOnClickListener {
            startActivity(Intent(this, com.cropsurvey.app.queue.QueueActivity::class.java))
        }
    }

    private fun setupSurveyCards() {
        btnCLS.setOnClickListener { startSurvey("CLS") }
        btnCHM.setOnClickListener { openChmVisitScreen() }  // CHM uses multi-visit flow
        btnCCE.setOnClickListener { startSurvey("CCE") }
    }

    /** CHM has its own Visit-1..5 entry screen — but goes through farmer verification first */
    private fun openChmVisitScreen() {
        val intent = Intent(this, FarmerVerificationActivity::class.java)
        intent.putExtra("survey_type", "CHM")
        startActivity(intent)
    }

    private fun startSurvey(type: String) {
        // Open farmer verification first — user chooses to verify or skip
        val intent = Intent(this, FarmerVerificationActivity::class.java)
        intent.putExtra("survey_type", type)
        startActivity(intent)
    }

    private fun setupRecyclerView() {
        surveysAdapter = RecentSurveysAdapter(
            surveys        = emptyList(),
            onEditDraft    = { survey -> openEditDraft(survey) },
            onSubmitDraft  = { survey -> openSubmitDraft(survey) },
            onDeleteDraft  = { survey -> confirmDeleteDraft(survey) },
            onEditRejected = { survey -> openEditRejected(survey) },
            onClick        = { survey ->
                Toast.makeText(this, "Case: ${survey.caseId}", Toast.LENGTH_SHORT).show()
            }
        )
        rvRecentSurveys.layoutManager = LinearLayoutManager(this)
        rvRecentSurveys.adapter = surveysAdapter
    }

    private fun setupTabs() {
        val tabs = listOf(tabDraft, tabSubmitted, tabApproved, tabRejected)
        val keys = listOf("draft", "submitted", "approved", "rejected")

        fun selectTab(index: Int) {
            tabs.forEachIndexed { i, tab ->
                if (i == index) {
                    tab.setBackgroundResource(R.drawable.tab_active)
                    tab.setTextColor(Color.WHITE)
                    tab.setTypeface(null, Typeface.BOLD)
                } else {
                    tab.setBackgroundResource(R.drawable.tab_inactive)
                    tab.setTextColor(Color.parseColor("#64748B"))
                    tab.setTypeface(null, Typeface.NORMAL)
                }
            }
        }

        selectTab(0)

        tabs.forEachIndexed { i, tab ->
            tab.setOnClickListener {
                currentTab = keys[i]
                selectTab(i)
                applyTabFilter()
            }
        }
    }

    // FIX 3: Tab filtering matches what the tab label says
    private fun applyTabFilter() {
        val filtered = when (currentTab) {
            "draft"     -> allSurveys.filter { it.status == "draft" || it.status == "submitted_pending" }
            "submitted" -> allSurveys.filter { it.status == "submitted" || it.status == "under_qc" }
            "approved"  -> allSurveys.filter { it.status == "approved" }
            "rejected"  -> allSurveys.filter { it.status == "rejected" }
            else        -> allSurveys
        }
        showSurveys(filtered)
    }

    private fun loadRecentSurveys() {
        showLoader("Connecting...")
        lifecycleScope.launch {
            try {
                val res = ApiClient.service.getSurveys(page = 1, limit = 100)
                when {
                    res.isSuccessful -> {
                        showLoader("Loading...")
                        val raw = res.body()?.data ?: emptyList()

                        val queuedSubmitIds = QueueManager.getItems(this@DashboardActivity)
                            .filter { it.type == "submit" }
                            .map { it.surveyId }
                            .toSet()

                        allSurveys = raw.map { survey ->
                            if (survey.status == "draft" && survey.id in queuedSubmitIds)
                                survey.copy(status = "submitted_pending")
                            else survey
                        }.sortedByDescending { it.updatedAt }

                        updateStatCards(allSurveys)
                        updateTabLabels(allSurveys)
                        applyTabFilter()
                    }
                    res.code() == 401 -> {
                        showLoader("Session expired...")
                        Toast.makeText(this@DashboardActivity,
                            "Session expired. Please log in again.", Toast.LENGTH_LONG).show()
                        SessionManager.clearSession()
                        SurveySession.reset()
                        val intent = Intent(this@DashboardActivity,
                            com.cropsurvey.app.auth.LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    }
                    else -> {
                        showLoader("Server down")
                        Toast.makeText(this@DashboardActivity,
                            "Failed to load surveys (${res.code()}). Pull down to retry.",
                            Toast.LENGTH_LONG).show()
                        showSurveys(emptyList())
                    }
                }
            } catch (e: Exception) {
                showLoader("No connection")
                Toast.makeText(this@DashboardActivity,
                    "No connection — showing cached data.", Toast.LENGTH_SHORT).show()
                showSurveys(emptyList())
            } finally {
                hideLoader()
            }
        }
    }

    private fun showLoader(status: String) {
        tvLoadingStatus.text = status
        layoutLoading.visibility = View.VISIBLE
        startDotAnimation()
    }

    private fun hideLoader() {
        layoutLoading.visibility = View.GONE
        dotAnimator?.cancel()
        dotAnimator = null
    }

    private fun startDotAnimation() {
        dotAnimator?.cancel()
        val dots = listOf(dot1, dot2, dot3)

        fun pulseFor(dot: View, delay: Long): ObjectAnimator {
            return ObjectAnimator.ofFloat(dot, "alpha", 0.3f, 1f, 0.3f).apply {
                duration = 900
                startDelay = delay
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
            }
        }

        dotAnimator = AnimatorSet().apply {
            playTogether(
                pulseFor(dots[0], 0L),
                pulseFor(dots[1], 300L),
                pulseFor(dots[2], 600L)
            )
            start()
        }
    }

    // FIX 3: Stat cards show ALL surveys (no month filter — that was the bug)
    private fun updateStatCards(surveys: List<Survey>) {
        tvStatDraft.text     = surveys.count { it.status == "draft" || it.status == "submitted_pending" }.toString()
        tvStatSubmitted.text = surveys.count { it.status == "submitted" || it.status == "under_qc" }.toString()
        tvStatApproved.text  = surveys.count { it.status == "approved" }.toString()
        tvStatRejected.text  = surveys.count { it.status == "rejected" }.toString()
    }

    // FIX 3: Show count on each tab, e.g. "Draft (2)" — at a glance how many per bucket
    private fun updateTabLabels(surveys: List<Survey>) {
        val draftCount     = surveys.count { it.status == "draft" || it.status == "submitted_pending" }
        val submittedCount = surveys.count { it.status == "submitted" || it.status == "under_qc" }
        val approvedCount  = surveys.count { it.status == "approved" }
        val rejectedCount  = surveys.count { it.status == "rejected" }

        tabDraft.text     = if (draftCount > 0)     getString(R.string.tab_draft_count, draftCount)     else getString(R.string.status_draft)
        tabSubmitted.text = if (submittedCount > 0) getString(R.string.tab_submitted_count, submittedCount) else getString(R.string.status_submitted)
        tabApproved.text  = if (approvedCount > 0)  getString(R.string.tab_approved_count, approvedCount)  else getString(R.string.approved)
        tabRejected.text  = if (rejectedCount > 0)  getString(R.string.tab_rejected_count, rejectedCount)  else getString(R.string.rejected)
    }

    private fun showSurveys(surveys: List<Survey>) {
        if (surveys.isEmpty()) {
            tvNoSurveys.visibility     = View.VISIBLE
            rvRecentSurveys.visibility = View.GONE
        } else {
            tvNoSurveys.visibility     = View.GONE
            rvRecentSurveys.visibility = View.VISIBLE
            surveysAdapter.updateSurveys(surveys)
        }
    }

    private fun confirmDeleteDraft(survey: Survey) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_deletion))
            .setMessage("Delete this draft?\n\nCase: ${survey.caseId}\n\nThis cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteDraft(survey) }
            .setNegativeButton("Cancel", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun deleteDraft(survey: Survey) {
        showLoader("Deleting...")
        lifecycleScope.launch {
            try {
                val res = ApiClient.service.deleteSurvey(survey.id)
                if (res.isSuccessful || res.code() in 200..299) {
                    allSurveys = allSurveys.filter { it.id != survey.id }
                    updateStatCards(allSurveys)
                    updateTabLabels(allSurveys)
                    applyTabFilter()
                    Toast.makeText(this@DashboardActivity, "Survey deleted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@DashboardActivity, "Delete failed (${res.code()})", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@DashboardActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                hideLoader()
            }
        }
    }

    private fun openEditDraft(survey: Survey) {
        showLoader("Loading draft...")
        lifecycleScope.launch {
            try {
                // Fetch FULL survey from server — list API omits form_data to save bandwidth
                val res = ApiClient.service.getSurvey(survey.id)
                val full = if (res.isSuccessful && res.body() != null) res.body()!! else survey
                SurveySession.restoreForDraftEdit(full.surveyType, full.id, full.formData)
                SurveySession.currentCaseId = full.caseId ?: ""
                restorePolygon(full)
                fetchAndRestoreServerPhotos(full.id)
            } catch (e: Exception) {
                // Offline fallback — use cached data
                SurveySession.restoreForDraftEdit(survey.surveyType, survey.id, survey.formData)
                SurveySession.currentCaseId = survey.caseId ?: ""
                restorePolygon(survey)
            } finally {
                hideLoader()
            }
            val intent = Intent(this@DashboardActivity, SurveyTabsActivity::class.java)
            intent.putExtra("survey_type", survey.surveyType)
            intent.putExtra("survey_id", survey.id)
            intent.putExtra("is_edit_mode", true)
            startActivity(intent)
        }
    }

    private fun openSubmitDraft(survey: Survey) {
        showLoader("Loading draft...")
        lifecycleScope.launch {
            try {
                val res = ApiClient.service.getSurvey(survey.id)
                val full = if (res.isSuccessful && res.body() != null) res.body()!! else survey
                SurveySession.restoreForDraftEdit(full.surveyType, full.id, full.formData)
                SurveySession.currentCaseId = full.caseId ?: ""
                restorePolygon(full)
                fetchAndRestoreServerPhotos(full.id)
            } catch (e: Exception) {
                SurveySession.restoreForDraftEdit(survey.surveyType, survey.id, survey.formData)
                SurveySession.currentCaseId = survey.caseId ?: ""
                restorePolygon(survey)
            } finally {
                hideLoader()
            }
            val intent = Intent(this@DashboardActivity, SubmitSurveyActivity::class.java)
            intent.putExtra("survey_type", survey.surveyType)
            intent.putExtra("survey_id", survey.id)
            startActivity(intent)
        }
    }

    /**
     * Fetches photos already on the server for this survey and populates
     * SurveySession.capturedPhotos so the photo list shows them as captured.
     * Uses the signed_url (or storage_url) as the localUri so thumbnails load.
     */
    private suspend fun fetchAndRestoreServerPhotos(surveyId: String) {
        try {
            val res = ApiClient.service.getPhotos(surveyId)
            if (res.isSuccessful) {
                res.body()?.forEach { serverPhoto ->
                    // Only add if not already tracked (avoid overwriting a freshly retaken photo)
                    if (!SurveySession.capturedPhotos.containsKey(serverPhoto.photoKey)) {
                        SurveySession.capturedPhotos[serverPhoto.photoKey] = CapturedPhoto(
                            photoKey = serverPhoto.photoKey,
                            localUri = serverPhoto.signedUrl ?: serverPhoto.storageUrl,
                            lat      = serverPhoto.lat ?: 0.0,
                            lon      = serverPhoto.lon ?: 0.0,
                            accuracy = serverPhoto.accuracy?.toFloat(),
                            uploaded = true   // already on server
                        )
                    }
                }
            }
        } catch (_: Exception) { /* non-fatal — user can still retake */ }
    }

    /**
     * FIX 4: Open a rejected survey for re-edit WITHOUT wiping capturedPhotos.
     * Uses the new SurveySession.restoreForRejectedEdit() which skips capturedPhotos.clear().
     * The photo upload endpoint (POST /surveys/:id/photos) already handles "upsert by key",
     * so retaking a photo automatically deletes the old storage file.
     */
    private fun openEditRejected(survey: Survey) {
        SurveySession.restoreForRejectedEdit(
            type     = survey.surveyType,
            surveyId = survey.id,
            data     = survey.formData
        )
        SurveySession.currentCaseId = survey.caseId ?: ""
        restorePolygon(survey)
        // Fetch existing server photos so already-uploaded ones show as captured
        lifecycleScope.launch { fetchAndRestoreServerPhotos(survey.id) }

        val intent = Intent(this, SurveyTabsActivity::class.java)
        intent.putExtra("survey_type", survey.surveyType)
        intent.putExtra("survey_id", survey.id)
        intent.putExtra("is_edit_mode", true)
        intent.putExtra("is_resubmit", true)
        startActivity(intent)
    }

    /** Restore polygon GeoJSON from survey.polygonGeoJson or form_data["polygon_geojson"] */
    @Suppress("UNCHECKED_CAST")
    private fun restorePolygon(survey: Survey) {
        val pg = survey.polygonGeoJson
            ?: (survey.formData["polygon_geojson"] as? Map<String, Any?>)
        pg?.let { SurveySession.restorePolygonForResubmit(it) }
    }

    private fun updateQueueBadge() {
        val count = QueueManager.getPendingCount(this)
        if (count > 0) {
            tvQueueBadge.visibility = View.VISIBLE
            tvQueueBadge.text = if (count > 9) "9+" else count.toString()
        } else {
            tvQueueBadge.visibility = View.GONE
        }
    }

    /** Always fetches fresh employee ID from API and saves to both SessionManager + SurveySession */
    private fun refreshEmployeeId(user: com.cropsurvey.app.models.User) {
        // Set immediately from cache (could be stale) so there's no delay for surveys
        SurveySession.employeeId = SessionManager.getCachedEmployeeId()
            ?.ifBlank { null }
            ?: user.employeeId?.ifBlank { null }
                    ?: user.id.take(8).uppercase()

        // Always refresh from API in background to fix stale cache
        lifecycleScope.launch {
            try {
                val res = ApiClient.service.getMe()
                if (res.isSuccessful) {
                    val freshUser = res.body() ?: return@launch
                    val empId = freshUser.employeeId?.ifBlank { null } ?: freshUser.id.take(8).uppercase()
                    // Cache it separately so it's always available even before next login
                    SessionManager.cacheEmployeeId(empId)
                    SurveySession.employeeId = empId
                    // Also update the full user JSON so other fields stay fresh
                    val token = SessionManager.getAccessToken() ?: ""
                    val refresh = SessionManager.getRefreshToken() ?: ""
                    SessionManager.saveSession(token, refresh, freshUser)
                }
            } catch (e: Exception) { /* non-fatal */ }
        }
    }

    private fun getGreeting(): String {
        return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11  -> getString(R.string.greeting_morning)
            in 12..16 -> getString(R.string.greeting_afternoon)
            else      -> getString(R.string.greeting_evening)
        }
    }
}