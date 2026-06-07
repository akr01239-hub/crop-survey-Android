package com.cropsurvey.app.survey

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.cropsurvey.app.BaseActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.cropsurvey.app.R
import com.cropsurvey.app.camera.PhotoTabFragment
import com.cropsurvey.app.config.AppConfig
import com.cropsurvey.app.models.UpdateSurveyRequest
import com.cropsurvey.app.network.ApiClient
import com.cropsurvey.app.queue.QueueManager
import com.cropsurvey.app.survey.cce.CCEFormFragment
import com.cropsurvey.app.survey.chm.CHMFormFragment
import com.cropsurvey.app.survey.cls.CLSFormFragment
import com.cropsurvey.app.utils.SurveySession
import com.cropsurvey.app.guide.AiGuideOverlay
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Unified tabbed survey activity.
 *
 * The user can freely switch between the Form tab and the Photos tab at any time.
 * All data (form fields + captured photos) is preserved in SurveySession in memory
 * and auto-synced to the server every 15 seconds. Manually tapping "Save Draft"
 * triggers an immediate sync. Nothing is lost on tab switch.
 *
 * Replaces the old two-activity flow: SurveyFormActivity → PhotoSelectionActivity.
 */
class SurveyTabsActivity : BaseActivity() {

    companion object {
        const val TAB_FORM   = 0
        const val TAB_PHOTOS = 1
    }

    // ── Views ──────────────────────────────────────────────────────────────────
    private lateinit var tvTitle:              TextView
    private lateinit var tvCaseId:             TextView
    private lateinit var btnBack:              android.widget.ImageButton
    private lateinit var btnSaveDraft:         Button
    private lateinit var btnProceedSubmit:     Button
    private lateinit var bannerResubmit:       View
    private lateinit var tvBannerMsg:          TextView
    private lateinit var bannerFarmerVerified: View
    private lateinit var tvFarmerVerifiedPhone:TextView
    private lateinit var tvSyncStatus:         TextView
    private lateinit var syncStatusChip:       View

    // Tab labels / indicators
    private lateinit var tabForm:              View
    private lateinit var tabPhotos:            View
    private lateinit var tvTabForm:            TextView
    private lateinit var tvTabFormSub:         TextView
    private lateinit var tvTabPhotos:          TextView
    private lateinit var tvTabPhotosSub:       TextView
    private lateinit var tabFormIndicator:     View
    private lateinit var tabPhotosIndicator:   View

    private lateinit var viewPager:            ViewPager2

    // ── State ─────────────────────────────────────────────────────────────────
    private lateinit var surveyType: String
    private lateinit var surveyId:   String
    private var isEditMode   = false
    private var isResubmit   = false
    private var farmerVerified = false
    private var farmerPhone    = ""

    private var autoSyncJob: Job? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_survey_tabs)

        surveyType     = intent.getStringExtra("survey_type")   ?: "CLS"
        surveyId       = intent.getStringExtra("survey_id")     ?: ""
        isEditMode     = intent.getBooleanExtra("is_edit_mode", false)
        isResubmit     = intent.getBooleanExtra("is_resubmit",  false)
        farmerVerified = intent.getBooleanExtra("farmer_verified", false)
        farmerPhone    = intent.getStringExtra("farmer_phone")  ?: ""

        bindViews()
        syncFarmerData()
        setupHeader()
        setupBanners()
        setupViewPager()
        window.decorView.post {
            AiGuideOverlay.show(this, AiGuideOverlay.Step.FORM_OPEN)
        }
        setupTabs()
        setupBottomBar()
        startAutoSync()

        // If starting on resubmit, restore previously-uploaded photos into session
        if (isResubmit) loadExistingPhotosForResubmit()
    }

    override fun onDestroy() {
        super.onDestroy()
        autoSyncJob?.cancel()
    }

    // ── View binding ──────────────────────────────────────────────────────────
    private fun bindViews() {
        tvTitle               = findViewById(R.id.tv_title)
        tvCaseId              = findViewById(R.id.tv_case_id)
        btnBack               = findViewById(R.id.btn_back)
        btnSaveDraft          = findViewById(R.id.btn_save_draft)
        btnProceedSubmit      = findViewById(R.id.btn_proceed_submit)
        bannerResubmit        = findViewById(R.id.banner_resubmit)
        tvBannerMsg           = findViewById(R.id.tv_banner_msg)
        bannerFarmerVerified  = findViewById(R.id.banner_farmer_verified)
        tvFarmerVerifiedPhone = findViewById(R.id.tv_farmer_verified_phone)
        tvSyncStatus          = findViewById(R.id.tv_sync_status)
        syncStatusChip        = findViewById(R.id.sync_status_chip)
        tabForm               = findViewById(R.id.tab_form)
        tabPhotos             = findViewById(R.id.tab_photos)
        tvTabForm             = findViewById(R.id.tv_tab_form)
        tvTabFormSub          = findViewById(R.id.tv_tab_form_sub)
        tvTabPhotos           = findViewById(R.id.tv_tab_photos)
        tvTabPhotosSub        = findViewById(R.id.tv_tab_photos_sub)
        tabFormIndicator      = findViewById(R.id.tab_form_indicator)
        tabPhotosIndicator    = findViewById(R.id.tab_photos_indicator)
        viewPager             = findViewById(R.id.survey_view_pager)
    }

    // ── Farmer data sync ──────────────────────────────────────────────────────
    private fun syncFarmerData() {
        if (farmerVerified && farmerPhone.isNotEmpty()) {
            SurveySession.formData["farmer_phone"]    = farmerPhone
            SurveySession.formData["farmer_verified"] = "yes"
        } else if (SurveySession.formData["farmer_verified"]?.toString() == "yes") {
            farmerVerified = true
            farmerPhone = SurveySession.formData["farmer_phone"]?.toString() ?: ""
        }
    }

    // ── Header ────────────────────────────────────────────────────────────────
    private fun setupHeader() {
        val baseTitle = when (surveyType) {
            "CLS" -> "Crop Loss Survey"
            "CHM" -> "Crop Health Monitoring"
            "CCE" -> "Crop Cutting Experiment"
            else  -> "Survey Form"
        }
        tvTitle.text  = if (isResubmit) "Re-edit: $baseTitle" else baseTitle
        tvCaseId.text = "Case: ${surveyId.take(8)}…"

        btnBack.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Leave Survey?")
                .setMessage("Your progress is auto-saved every 15 seconds. Save now and leave?")
                .setPositiveButton("Save & Leave") { _, _ -> saveDraft { finish() } }
                .setNegativeButton("Discard Changes") { _, _ -> finish() }
                .setNeutralButton("Cancel", null)
                .show()
        }
    }

    // ── Banners ───────────────────────────────────────────────────────────────
    private fun setupBanners() {
        // Farmer verified
        if (farmerVerified && farmerPhone.isNotEmpty()) {
            bannerFarmerVerified.visibility = View.VISIBLE
            tvFarmerVerifiedPhone.text = "✅ Farmer Verified: +91-$farmerPhone"
        } else {
            bannerFarmerVerified.visibility = View.GONE
        }

        // Resubmit / edit
        when {
            isResubmit -> {
                bannerResubmit.visibility = View.VISIBLE
                tvBannerMsg.text = "⚠ Rejected survey — update the details and photos, then submit."
            }
            isEditMode -> {
                bannerResubmit.visibility = View.VISIBLE
                tvBannerMsg.text = "✏ Editing draft — make changes and tap Save Draft or Submit."
            }
            else -> bannerResubmit.visibility = View.GONE
        }
    }

    // ── ViewPager + adapter ───────────────────────────────────────────────────
    private fun setupViewPager() {
        viewPager.adapter = SurveyPagerAdapter()
        viewPager.isUserInputEnabled = false   // only switch via tab taps (prevents accidental swipe mid-form)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                highlightTab(position)
                refreshPhotoCount()
                if (position == TAB_PHOTOS) {
                    refreshPhotoCount()
                    // Guide: user switched to Photos tab
                    AiGuideOverlay.show(this@SurveyTabsActivity, AiGuideOverlay.Step.PHOTOS_TAB)
                }
            }
        })
    }

    // Keep a direct reference so collectAndStoreFormData() can always reach it
    private var formFragment: Fragment? = null

    inner class SurveyPagerAdapter : FragmentStateAdapter(this) {
        override fun getItemCount() = 2
        override fun createFragment(position: Int): Fragment = when (position) {
            TAB_FORM   -> {
                val f = when (surveyType) {
                    "CLS" -> CLSFormFragment()
                    "CHM" -> CHMFormFragment()
                    "CCE" -> CCEFormFragment()
                    else  -> CLSFormFragment()
                }
                formFragment = f
                f
            }
            TAB_PHOTOS -> PhotoTabFragment.newInstance(surveyType, surveyId, isResubmit)
            else       -> CLSFormFragment()
        }
    }

    // ── Tab UI ────────────────────────────────────────────────────────────────
    private fun setupTabs() {
        tabForm.setOnClickListener   { switchToTab(TAB_FORM) }
        tabPhotos.setOnClickListener { switchToTab(TAB_PHOTOS) }
        highlightTab(TAB_FORM)
    }

    fun switchToTab(tab: Int) {
        // Collect form data from fragment before leaving the form tab
        if (viewPager.currentItem == TAB_FORM && tab == TAB_PHOTOS) {
            collectAndStoreFormData()
            AiGuideOverlay.jumpToStep(this, AiGuideOverlay.Step.PHOTOS_TAB)
        }
        viewPager.currentItem = tab
        highlightTab(tab)
        if (tab == TAB_PHOTOS) refreshPhotoCount()
    }

    private fun highlightTab(active: Int) {
        val activeColor  = android.graphics.Color.parseColor("#2563EB")
        val inactiveColor = android.graphics.Color.parseColor("#64748B")

        tvTabForm.setTextColor(   if (active == TAB_FORM)   activeColor else inactiveColor)
        tvTabPhotos.setTextColor( if (active == TAB_PHOTOS) activeColor else inactiveColor)
        tabFormIndicator.visibility   = if (active == TAB_FORM)   View.VISIBLE else View.INVISIBLE
        tabPhotosIndicator.visibility = if (active == TAB_PHOTOS) View.VISIBLE else View.INVISIBLE
    }

    fun refreshPhotoCount() {
        val (captured, total) = SurveySession.getPhotoCount(surveyType)
        tvTabPhotosSub.text = "$captured / $total captured"
        tvTabFormSub.text   = if (SurveySession.formData.size > 3) "Partially filled" else "Fill details"
    }

    // ── Form data collection ──────────────────────────────────────────────────

    /**
     * Collects WITHOUT validation — used for auto-save and manual draft save.
     * Always succeeds even if form is partially filled.
     */
    private fun collectAndStoreFormData() {
        val fragment = formFragment ?: supportFragmentManager.findFragmentByTag("f0")
        val data = when (fragment) {
            is CLSFormFragment -> fragment.collectDraftData()
            is CHMFormFragment -> fragment.collectDraftData()
            is CCEFormFragment -> fragment.collectDraftData()
            else               -> emptyMap()
        }
        if (data.isNotEmpty()) {
            val full = data.toMutableMap()
            if (farmerVerified && farmerPhone.isNotEmpty()) {
                full["farmer_verified"] = true
                full["farmer_phone"]    = farmerPhone
            }
            SurveySession.updateFormData(full)
        }
    }

    // ── Bottom action bar ─────────────────────────────────────────────────────
    private fun setupBottomBar() {
        btnSaveDraft.setOnClickListener {
            collectAndStoreFormData()
            AiGuideOverlay.show(this, AiGuideOverlay.Step.SAVE_DRAFT)
            saveDraft {
                AiGuideOverlay.advance(this)
                Toast.makeText(this, "Draft saved ✓", Toast.LENGTH_SHORT).show()
            }
        }

        btnProceedSubmit.setOnClickListener {
            collectAndStoreFormData()

            // Validate form
            val formFrag = formFragment ?: supportFragmentManager.findFragmentByTag("f0")
            val formValid = when (formFrag) {
                is CLSFormFragment -> formFrag.validateRequiredFields()
                is CHMFormFragment -> formFrag.validateRequiredFields()
                is CCEFormFragment -> formFrag.validateRequiredFields()
                else               -> true
            }
            if (!formValid) {
                switchToTab(TAB_FORM)
                Toast.makeText(this, "Please complete all required fields in the Form tab", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Validate photos
            val requirements = AppConfig.getPhotosForType(this, surveyType)
            val capturedKeys = SurveySession.capturedPhotos.keys
            val missing = requirements.filter { it.required && it.key !in capturedKeys }
            if (missing.isNotEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("Missing Photos")
                    .setMessage("Please capture: ${missing.take(3).joinToString(", ") { it.label }}${if (missing.size > 3) " and ${missing.size - 3} more…" else ""}")
                    .setPositiveButton("Go to Photos") { _, _ -> switchToTab(TAB_PHOTOS) }
                    .setNegativeButton("Cancel", null)
                    .show()
                return@setOnClickListener
            }

            // All good → show SUBMIT guide step, save draft then go to submit screen
            AiGuideOverlay.show(this, AiGuideOverlay.Step.SUBMIT)
            saveDraft {
                AiGuideOverlay.advance(this)  // mark guide complete
                val intent = Intent(this, SubmitSurveyActivity::class.java)
                intent.putExtra("survey_type", surveyType)
                intent.putExtra("survey_id",   surveyId)
                intent.putExtra("is_resubmit", isResubmit)
                startActivity(intent)
            }
        }
    }

    // ── Draft save ────────────────────────────────────────────────────────────
    private fun saveDraft(onSuccess: (() -> Unit)? = null) {
        AiGuideOverlay.show(this, AiGuideOverlay.Step.SAVE_DRAFT)
        SurveySession.savePhotosDraft()  // always persist photos locally first
        lifecycleScope.launch {
            setSyncStatus("syncing")
            try {
                val res = ApiClient.service.updateSurvey(
                    surveyId,
                    UpdateSurveyRequest(SurveySession.formData, SurveySession.polygonGeoJson)
                )
                if (res.isSuccessful) {
                    setSyncStatus("synced")
                    onSuccess?.invoke()
                } else {
                    QueueManager.enqueueFormUpdate(this@SurveyTabsActivity, surveyId, SurveySession.formData)
                    setSyncStatus("offline")
                    onSuccess?.invoke()
                }
            } catch (e: Exception) {
                QueueManager.enqueueFormUpdate(this@SurveyTabsActivity, surveyId, SurveySession.formData)
                setSyncStatus("offline")
                onSuccess?.invoke()
            }
        }
    }

    // ── Auto-sync every 15 seconds ────────────────────────────────────────────
    private fun startAutoSync() {
        autoSyncJob = lifecycleScope.launch {
            while (isActive) {
                delay(15_000L)  // 15 seconds
                if (!isActive) break
                // collectAndStoreFormData touches fragments — must run on main thread
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    collectAndStoreFormData()
                }
                SurveySession.savePhotosDraft()
                try {
                    val res = ApiClient.service.updateSurvey(
                        surveyId,
                        UpdateSurveyRequest(SurveySession.formData, SurveySession.polygonGeoJson)
                    )
                    if (res.isSuccessful) setSyncStatus("synced")
                    else setSyncStatus("offline")
                } catch (e: Exception) {
                    setSyncStatus("offline")
                }
            }
        }
    }

    private fun setSyncStatus(state: String) {
        runOnUiThread {
            when (state) {
                "syncing" -> {
                    tvSyncStatus.text = "⟳ Saving…"
                    tvSyncStatus.setTextColor(android.graphics.Color.parseColor("#D97706"))
                }
                "synced" -> {
                    tvSyncStatus.text = "● Synced"
                    tvSyncStatus.setTextColor(android.graphics.Color.parseColor("#16A34A"))
                }
                "offline" -> {
                    tvSyncStatus.text = "○ Offline"
                    tvSyncStatus.setTextColor(android.graphics.Color.parseColor("#DC2626"))
                }
            }
        }
    }

    // ── Load existing photos for rejected-survey resubmit ─────────────────────
    private fun loadExistingPhotosForResubmit() {
        lifecycleScope.launch {
            try {
                val res = ApiClient.service.getPhotos(surveyId)
                if (res.isSuccessful) {
                    val serverPhotos = res.body() ?: emptyList()
                    for (sp in serverPhotos) {
                        if (!SurveySession.capturedPhotos.containsKey(sp.photoKey)) {
                            SurveySession.capturedPhotos[sp.photoKey] = com.cropsurvey.app.models.CapturedPhoto(
                                photoKey = sp.photoKey,
                                localUri = sp.signedUrl ?: sp.storageUrl,
                                lat      = sp.lat ?: 0.0,
                                lon      = sp.lon ?: 0.0,
                                accuracy = null,
                                uploaded = true
                            )
                        }
                    }
                    refreshPhotoCount()
                }
            } catch (_: Exception) { /* offline — user will see 0/N and can retake */ }
        }
    }
}