package com.cropsurvey.app.guide

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.RecyclerView
import com.cropsurvey.app.BaseActivity
import com.cropsurvey.app.R
import com.cropsurvey.app.dashboard.DashboardActivity

/**
 * OnboardingGuideActivity
 *
 * Shown ONCE right after the very first login (and never again unless explicitly
 * triggered from settings).  Walks the user through 6 slides:
 *   1. Welcome
 *   2. Device lock & 2-device rule
 *   3. Mock / fake GPS ban
 *   4. Survey types (CLS / CHM / CCE)
 *   5. Photo requirements
 *   6. Draft → Submit → Approval flow
 *
 * Tracking: a single boolean in "crop_survey_guide" SharedPreferences.
 */
class OnboardingGuideActivity : BaseActivity() {

    companion object {
        private const val PREF_GUIDE = "crop_survey_guide"
        private const val KEY_SEEN   = "onboarding_seen"

        /** Call from DashboardActivity.onCreate — shows guide if never seen before */
        fun showIfNeeded(activity: android.app.Activity) {
            val prefs = activity.getSharedPreferences(PREF_GUIDE, android.content.Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_SEEN, false)) {
                activity.startActivity(Intent(activity, OnboardingGuideActivity::class.java))
            }
        }

        /** Call from a Settings menu to re-show the guide anytime */
        fun resetAndShow(activity: android.app.Activity) {
            val prefs = activity.getSharedPreferences(PREF_GUIDE, android.content.Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_SEEN, false).apply()
            activity.startActivity(Intent(activity, OnboardingGuideActivity::class.java))
        }
    }

    private lateinit var viewPager: ViewPager2
    private lateinit var btnNext: Button
    private lateinit var btnSkip: TextView
    private lateinit var dotsContainer: LinearLayout

    private val slides = buildSlides()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding_guide)

        viewPager       = findViewById(R.id.vp_onboarding)
        btnNext         = findViewById(R.id.btn_guide_next)
        btnSkip         = findViewById(R.id.tv_guide_skip)
        dotsContainer   = findViewById(R.id.ll_guide_dots)

        viewPager.adapter = SlideAdapter(slides)
        viewPager.isUserInputEnabled = true

        buildDots()
        updateUI(0)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updateUI(position)
        })

        btnNext.setOnClickListener {
            val cur = viewPager.currentItem
            if (cur < slides.lastIndex) {
                viewPager.currentItem = cur + 1
            } else {
                finish()
            }
        }

        btnSkip.setOnClickListener { finish() }
    }

    override fun finish() {
        // Mark as seen so it never auto-shows again
        getSharedPreferences(PREF_GUIDE, MODE_PRIVATE)
            .edit().putBoolean(KEY_SEEN, true).apply()
        super.finish()
    }

    private fun updateUI(pos: Int) {
        // Dots
        for (i in 0 until dotsContainer.childCount) {
            val dot = dotsContainer.getChildAt(i)
            dot.alpha = if (i == pos) 1f else 0.35f
            val size = if (i == pos) dp(10) else dp(8)
            dot.layoutParams = (dot.layoutParams as LinearLayout.LayoutParams).also {
                it.width = size; it.height = size
            }
        }

        // Button label
        btnNext.text = if (pos == slides.lastIndex) "Got it! Let's Start" else "Next →"
        btnSkip.visibility = if (pos == slides.lastIndex) View.GONE else View.VISIBLE
    }

    private fun buildDots() {
        dotsContainer.removeAllViews()
        slides.forEachIndexed { i, _ ->
            val dot = View(this).apply {
                val size = dp(8)
                layoutParams = LinearLayout.LayoutParams(size, size).also {
                    it.marginEnd = dp(6)
                }
                setBackgroundResource(R.drawable.circle_primary)
                alpha = if (i == 0) 1f else 0.35f
            }
            dotsContainer.addView(dot)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ── Slide data ─────────────────────────────────────────────────────────────
    data class Slide(val emoji: String, val title: String, val body: String, val bgColor: Int)

    private fun buildSlides() = listOf(
        Slide(
            "👋",
            "Welcome to Krishi Sarvekshan",
            "This quick guide will explain how the app works, what's allowed, and how to complete your first survey correctly.\n\nRead each page carefully — it takes less than 2 minutes.",
            0xFF1B5E20.toInt()
        ),
        Slide(
            "📱",
            "One Device Per Account",
            "⚠️ Your account is LOCKED to the first device you log in from.\n\n• Logging in from a second device will IMMEDIATELY log you out of the first.\n• You cannot use 2 devices at the same time.\n• If you change your phone, contact your Admin to unlock the account.\n\nThis protects data integrity and prevents duplicate surveys.",
            0xFF0D47A1.toInt()
        ),
        Slide(
            "🚫",
            "No Fake / Mock GPS",
            "The app DETECTS mock location apps (GPS spoofers, fake location tools).\n\n• If a mock location is detected, the app will CLOSE immediately.\n• Surveys with fake GPS coordinates will be REJECTED.\n• Using mock GPS is a violation and can lead to account suspension.\n\nAlways go to the actual survey location before starting.",
            0xFFB71C1C.toInt()
        ),
        Slide(
            "📋",
            "Survey Types",
            "The app supports 3 survey types:\n\n🟢  CLS — Crop Loss Survey\nDocument crop damage, loss %, cause of event, bank details, and government officer info.\n\n🔵  CHM — Crop Health Monitoring\nMonitor crop health with up to 5 visits per case. Draw field boundary on the map.\n\n🟠  CCE — Crop Cutting Experiment\nRecord grain weights, plot size, threshing data, and yield calculations.",
            0xFF4A148C.toInt()
        ),
        Slide(
            "📸",
            "Photos Are Important",
            "Each survey has required and optional photos.\n\n• Required photos MUST be captured before you can submit.\n• Photos are geo-tagged automatically with GPS coordinates and timestamp.\n• Use the camera in the app — do NOT import from gallery.\n• Take clear, well-lit photos from the correct angle as labeled.\n\nPhotos are evidence — blurry or wrong photos will cause rejection.",
            0xFFE65100.toInt()
        ),
        Slide(
            "✅",
            "Draft → Submit → Approval",
            "Here's how a survey goes from start to finish:\n\n1️⃣  Fill the form + take photos\n2️⃣  Tap Save Draft anytime to save progress\n3️⃣  When ready, tap Submit\n4️⃣  Survey goes to QC review\n5️⃣  Approved ✅ or Rejected ❌\n\nIf rejected, you can edit and resubmit.\n\nYou're now ready to start your first survey! Tap the AI Guide button on the dashboard anytime for step-by-step help.",
            0xFF1B5E20.toInt()
        )
    )

    // ── RecyclerView Adapter ───────────────────────────────────────────────────
    inner class SlideAdapter(private val items: List<Slide>) :
        RecyclerView.Adapter<SlideAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val emoji: TextView  = v.findViewById(R.id.tv_slide_emoji)
            val title: TextView  = v.findViewById(R.id.tv_slide_title)
            val body:  TextView  = v.findViewById(R.id.tv_slide_body)
            val card:  View      = v.findViewById(R.id.card_slide)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_onboarding_slide, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val s = items[pos]
            h.emoji.text = s.emoji
            h.title.text = s.title
            h.body.text  = s.body
            h.card.setBackgroundColor(s.bgColor)
        }
    }
}
