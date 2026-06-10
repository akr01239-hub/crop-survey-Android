package com.cropsurvey.app.guide

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.RecyclerView
import com.cropsurvey.app.BaseActivity
import com.cropsurvey.app.auth.LoginActivity
import com.cropsurvey.app.R

/**
 * OnboardingGuideActivity
 *
 * Shown ONCE right after the very first login (and never again unless explicitly
 * triggered from settings). All slide content now comes from strings.xml so it
 * is automatically translated to whichever language the user selected before login.
 *
 * Slides:
 *   1. Welcome
 *   2. Device lock & 2-device rule
 *   3. Mock / fake GPS ban
 *   4. Survey types (CLS / CHM / CCE)
 *   5. Photo requirements
 *   6. Draft → Submit → Approval flow
 */
class OnboardingGuideActivity : BaseActivity() {

    companion object {
        private const val PREF_GUIDE       = "crop_survey_guide"
        private const val KEY_SEEN         = "onboarding_seen"

        /**
         * Call after every successful login.
         * Shows onboarding ONCE ever — never again after first completion.
         */
        fun showIfNeeded(activity: android.app.Activity) {
            val prefs = activity.getSharedPreferences(PREF_GUIDE, android.content.Context.MODE_PRIVATE)
            val seen = prefs.getBoolean(KEY_SEEN, false)
            if (!seen) {
                activity.startActivity(Intent(activity, OnboardingGuideActivity::class.java))
            }
        }

        fun resetAndShow(activity: android.app.Activity) {
            val prefs = activity.getSharedPreferences(PREF_GUIDE, android.content.Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_SEEN, false)
                .putInt(KEY_LOGIN_COUNT, 0)  // reset count so it shows again
                .apply()
            activity.startActivity(Intent(activity, OnboardingGuideActivity::class.java))
        }
    }

    private lateinit var viewPager: ViewPager2
    private lateinit var btnNext: Button
    private lateinit var btnSkip: TextView
    private lateinit var dotsContainer: LinearLayout

    private val slides by lazy { buildSlides() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding_guide)

        viewPager     = findViewById(R.id.vp_onboarding)
        btnNext       = findViewById(R.id.btn_guide_next)
        btnSkip       = findViewById(R.id.tv_guide_skip)
        dotsContainer = findViewById(R.id.ll_guide_dots)

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
        getSharedPreferences(PREF_GUIDE, MODE_PRIVATE)
            .edit().putBoolean(KEY_SEEN, true).apply()
        // After onboarding: go to Terms if not yet accepted, else Dashboard
        val dest = if (!com.cropsurvey.app.auth.TermsAndConditionsActivity.hasAccepted(this))
            com.cropsurvey.app.auth.TermsAndConditionsActivity::class.java
        else
            com.cropsurvey.app.dashboard.DashboardActivity::class.java
        startActivity(Intent(this, dest))
        super.finish()
    }

    private fun updateUI(pos: Int) {
        for (i in 0 until dotsContainer.childCount) {
            val dot = dotsContainer.getChildAt(i)
            dot.alpha = if (i == pos) 1f else 0.35f
            val size = if (i == pos) dp(10) else dp(8)
            dot.layoutParams = (dot.layoutParams as LinearLayout.LayoutParams).also {
                it.width = size; it.height = size
            }
        }
        // Last slide: custom button label from strings.xml, hide Skip
        btnNext.text = if (pos == slides.lastIndex)
            getString(R.string.guide_slide6_btn)
        else
            getString(R.string.btn_next)
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

    // ── Slide data — all text from strings.xml → auto-translated ─────────────
    data class Slide(val emoji: String, val title: String, val body: String, val bgColor: Int)

    private fun buildSlides() = listOf(
        Slide("👋", getString(R.string.guide_slide1_title), getString(R.string.guide_slide1_body), 0xFF1B5E20.toInt()),
        Slide("📱", getString(R.string.guide_slide2_title), getString(R.string.guide_slide2_body), 0xFF0D47A1.toInt()),
        Slide("🚫", getString(R.string.guide_slide3_title), getString(R.string.guide_slide3_body), 0xFFB71C1C.toInt()),
        Slide("📋", getString(R.string.guide_slide4_title), getString(R.string.guide_slide4_body), 0xFF4A148C.toInt()),
        Slide("📸", getString(R.string.guide_slide5_title), getString(R.string.guide_slide5_body), 0xFFE65100.toInt()),
        Slide("✅", getString(R.string.guide_slide6_title), getString(R.string.guide_slide6_body), 0xFF1B5E20.toInt())
    )

    // ── RecyclerView Adapter ──────────────────────────────────────────────────
    inner class SlideAdapter(private val items: List<Slide>) :
        RecyclerView.Adapter<SlideAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val emoji: TextView = v.findViewById(R.id.tv_slide_emoji)
            val title: TextView = v.findViewById(R.id.tv_slide_title)
            val body:  TextView = v.findViewById(R.id.tv_slide_body)
            val card:  View     = v.findViewById(R.id.card_slide)
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
