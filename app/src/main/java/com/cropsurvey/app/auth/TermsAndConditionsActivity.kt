package com.cropsurvey.app.auth

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BulletSpan
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.cropsurvey.app.BaseActivity
import com.cropsurvey.app.R

/**
 * TermsAndConditionsActivity
 *
 * Shown ONCE after onboarding is completed.
 * User must scroll to bottom and check the checkbox before proceeding.
 * Once accepted, never shown again (stored in SharedPreferences).
 */
class TermsAndConditionsActivity : BaseActivity() {

    companion object {
        private const val PREF_TERMS   = "crop_survey_terms"
        private const val KEY_ACCEPTED = "terms_accepted"

        /** Returns true if user has already accepted terms */
        fun hasAccepted(activity: android.content.Context): Boolean {
            return activity.getSharedPreferences(PREF_TERMS, android.content.Context.MODE_PRIVATE)
                .getBoolean(KEY_ACCEPTED, false)
        }

        /** Mark terms as accepted */
        fun markAccepted(activity: android.content.Context) {
            activity.getSharedPreferences(PREF_TERMS, android.content.Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ACCEPTED, true).apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If already accepted, skip straight to login
        if (hasAccepted(this)) {
            goToLogin(); return
        }

        buildUI()
    }

    private fun buildUI() {
        val dp = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF8FFF8.toInt())
            layoutParams = android.view.ViewGroup.LayoutParams(-1, -1)
        }

        // ── Header ────────────────────────────────────────────────────────────
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1B6B2F.toInt())
            setPadding((20*dp).toInt(), (48*dp).toInt(), (20*dp).toInt(), (24*dp).toInt())
        }
        val tvTitle = TextView(this).apply {
            text = getString(R.string.terms_title)
            textSize = 22f; setTextColor(0xFFFFFFFF.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val tvSubtitle = TextView(this).apply {
            text = getString(R.string.terms_subtitle)
            textSize = 13f; setTextColor(0xCCFFFFFF.toInt())
            setPadding(0, (6*dp).toInt(), 0, 0)
        }
        header.addView(tvTitle); header.addView(tvSubtitle)
        root.addView(header)

        // ── Scrollable terms content ──────────────────────────────────────────
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            setPadding((20*dp).toInt(), (20*dp).toInt(), (20*dp).toInt(), (20*dp).toInt())
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val sections = listOf(
            Pair(getString(R.string.terms_section1_title), getString(R.string.terms_section1_body)),
            Pair(getString(R.string.terms_section2_title), getString(R.string.terms_section2_body)),
            Pair(getString(R.string.terms_section3_title), getString(R.string.terms_section3_body)),
            Pair(getString(R.string.terms_section4_title), getString(R.string.terms_section4_body)),
            Pair(getString(R.string.terms_section5_title), getString(R.string.terms_section5_body)),
        )

        for ((title, body) in sections) {
            val tvSectionTitle = TextView(this).apply {
                text = title; textSize = 15f
                setTextColor(0xFF1B6B2F.toInt())
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, (16*dp).toInt(), 0, (4*dp).toInt())
            }
            val tvSectionBody = TextView(this).apply {
                text = body; textSize = 13f
                setTextColor(0xFF374151.toInt())
                lineHeight = (20*dp).toInt()
            }
            contentLayout.addView(tvSectionTitle)
            contentLayout.addView(tvSectionBody)
        }

        // Divider
        val divider = View(this).apply {
            setBackgroundColor(0xFFE5E7EB.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, (1*dp).toInt()).also { it.topMargin = (20*dp).toInt() }
        }
        contentLayout.addView(divider)

        scrollView.addView(contentLayout)
        root.addView(scrollView)

        // ── Bottom section: checkbox + buttons ────────────────────────────────
        val bottomSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding((20*dp).toInt(), (16*dp).toInt(), (20*dp).toInt(), (24*dp).toInt())
            elevation = 8f * dp
        }

        val checkRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (12*dp).toInt())
        }
        val checkbox = CheckBox(this).apply {
            isChecked = false
            layoutParams = LinearLayout.LayoutParams((24*dp).toInt(), (24*dp).toInt())
        }
        val tvCheckLabel = TextView(this).apply {
            text = getString(R.string.terms_agree_label)
            textSize = 13f; setTextColor(0xFF374151.toInt())
            setPadding((10*dp).toInt(), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        checkRow.addView(checkbox); checkRow.addView(tvCheckLabel)
        bottomSection.addView(checkRow)

        val btnAccept = Button(this).apply {
            text = getString(R.string.terms_accept_btn)
            setBackgroundColor(0xFF9CA3AF.toInt()) // disabled gray
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f; isEnabled = false
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(-1, (50*dp).toInt()).also { it.bottomMargin = (8*dp).toInt() }
        }

        val btnDecline = Button(this).apply {
            text = getString(R.string.terms_decline_btn)
            setBackgroundColor(0x00000000); setTextColor(0xFF6B7280.toInt())
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(-1, (40*dp).toInt())
        }

        // Enable accept button only when checkbox is checked
        checkbox.setOnCheckedChangeListener { _, isChecked ->
            btnAccept.isEnabled = isChecked
            btnAccept.setBackgroundColor(if (isChecked) 0xFF1B6B2F.toInt() else 0xFF9CA3AF.toInt())
        }

        btnAccept.setOnClickListener {
            markAccepted(this)
            goToLogin()
        }

        btnDecline.setOnClickListener {
            // Decline → logout and go back to login
            android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.terms_decline_confirm_title))
                .setMessage(getString(R.string.terms_decline_confirm_msg))
                .setPositiveButton(getString(R.string.terms_decline_confirm_yes)) { _, _ ->
                    com.cropsurvey.app.utils.SessionManager.logout(this)
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent); finish()
                }
                .setNegativeButton(getString(R.string.terms_decline_confirm_no), null)
                .show()
        }

        bottomSection.addView(btnAccept)
        bottomSection.addView(btnDecline)
        root.addView(bottomSection)

        setContentView(root)
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    // Prevent back button from skipping terms
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Do nothing — user must accept or decline
    }
}

