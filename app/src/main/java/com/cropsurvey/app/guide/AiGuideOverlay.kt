package com.cropsurvey.app.guide

import android.content.Context
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.cropsurvey.app.R

/**
 * AiGuideOverlay
 *
 * Step-by-step overlay guide shown during the survey workflow.
 * All message content now loaded from strings.xml → automatically shown in
 * whichever language the user selected before login (Hindi, Marathi, etc.).
 *
 * Guide journey (13 steps, 0-based):
 *   0  Dashboard intro
 *   1  Highlight CLS/CHM/CCE cards
 *   2  Farmer verification screen
 *   3  Skip or verify farmer
 *   4  Map screen (draw polygon)
 *   5  Form tab intro
 *   6  Fill Basic Information
 *   7  Fill Location
 *   8  Fill Crop Details
 *   9  Switch to Photos tab
 *   10 Capture required photos
 *   11 Save draft
 *   12 Submit survey — COMPLETE
 *
 * Usage:
 *   In each Activity/Fragment's onResume():
 *     AiGuideOverlay.show(activity, AiGuideOverlay.Step.DASHBOARD)
 *
 *   When user completes a step action:
 *     AiGuideOverlay.advance(context)
 */
object AiGuideOverlay {

    private const val PREF_GUIDE         = "crop_survey_ai_guide"
    private const val KEY_STEP           = "ai_guide_step"
    private const val KEY_ENABLED        = "ai_guide_enabled"
    private const val KEY_DONE           = "ai_guide_done"
    private const val KEY_SURVEYS_SUBMITTED = "surveys_submitted_count"
    private const val AUTO_GUIDE_SURVEYS = 2   // show guide automatically for first N surveys

    // ── Step definitions ──────────────────────────────────────────────────────
    enum class Step(val index: Int) {
        DASHBOARD(0),
        SURVEY_CARD_SELECT(1),
        FARMER_VERIFICATION(2),
        FARMER_VERIFIED(3),
        MAP_DRAW(4),
        FORM_OPEN(5),
        FORM_BASIC_INFO(6),
        FORM_LOCATION(7),
        FORM_CROP_DETAILS(8),
        PHOTOS_TAB(9),
        PHOTOS_CAPTURE(10),
        SAVE_DRAFT(11),
        SUBMIT(12);

        companion object {
            fun fromIndex(i: Int) = values().firstOrNull { it.index == i } ?: DASHBOARD
        }
    }

    data class GuideMessage(
        val title: String,
        val message: String,
        val tip: String? = null,
        val actionLabel: String = ""
    )

    /**
     * Build all messages from strings.xml for the current locale.
     * Called fresh every time show() is invoked so language changes are picked up.
     */
    private fun buildMessages(ctx: Context): Map<Step, GuideMessage> = mapOf(
        Step.DASHBOARD to GuideMessage(
            ctx.getString(R.string.guide_step0_title),
            ctx.getString(R.string.guide_step0_msg),
            ctx.getString(R.string.guide_step0_tip),
            ctx.getString(R.string.guide_step0_action)
        ),
        Step.SURVEY_CARD_SELECT to GuideMessage(
            ctx.getString(R.string.guide_step1_title),
            ctx.getString(R.string.guide_step1_msg),
            ctx.getString(R.string.guide_step1_tip),
            ctx.getString(R.string.guide_step1_action)
        ),
        Step.FARMER_VERIFICATION to GuideMessage(
            ctx.getString(R.string.guide_step2_title),
            ctx.getString(R.string.guide_step2_msg),
            ctx.getString(R.string.guide_step2_tip),
            ctx.getString(R.string.guide_step2_action)
        ),
        Step.FARMER_VERIFIED to GuideMessage(
            ctx.getString(R.string.guide_step3_title),
            ctx.getString(R.string.guide_step3_msg),
            ctx.getString(R.string.guide_step3_tip),
            ctx.getString(R.string.guide_step3_action)
        ),
        Step.MAP_DRAW to GuideMessage(
            ctx.getString(R.string.guide_step4_title),
            ctx.getString(R.string.guide_step4_msg),
            ctx.getString(R.string.guide_step4_tip),
            ctx.getString(R.string.guide_step4_action)
        ),
        Step.FORM_OPEN to GuideMessage(
            ctx.getString(R.string.guide_step5_title),
            ctx.getString(R.string.guide_step5_msg),
            ctx.getString(R.string.guide_step5_tip),
            ctx.getString(R.string.guide_step5_action)
        ),
        Step.FORM_BASIC_INFO to GuideMessage(
            ctx.getString(R.string.guide_step6_title),
            ctx.getString(R.string.guide_step6_msg),
            ctx.getString(R.string.guide_step6_tip),
            ctx.getString(R.string.guide_step6_action)
        ),
        Step.FORM_LOCATION to GuideMessage(
            ctx.getString(R.string.guide_step7_title),
            ctx.getString(R.string.guide_step7_msg),
            ctx.getString(R.string.guide_step7_tip),
            ctx.getString(R.string.guide_step7_action)
        ),
        Step.FORM_CROP_DETAILS to GuideMessage(
            ctx.getString(R.string.guide_step8_title),
            ctx.getString(R.string.guide_step8_msg),
            ctx.getString(R.string.guide_step8_tip),
            ctx.getString(R.string.guide_step8_action)
        ),
        Step.PHOTOS_TAB to GuideMessage(
            ctx.getString(R.string.guide_step9_title),
            ctx.getString(R.string.guide_step9_msg),
            ctx.getString(R.string.guide_step9_tip),
            ctx.getString(R.string.guide_step9_action)
        ),
        Step.PHOTOS_CAPTURE to GuideMessage(
            ctx.getString(R.string.guide_step10_title),
            ctx.getString(R.string.guide_step10_msg),
            ctx.getString(R.string.guide_step10_tip),
            ctx.getString(R.string.guide_step10_action)
        ),
        Step.SAVE_DRAFT to GuideMessage(
            ctx.getString(R.string.guide_step11_title),
            ctx.getString(R.string.guide_step11_msg),
            ctx.getString(R.string.guide_step11_tip),
            ctx.getString(R.string.guide_step11_action)
        ),
        Step.SUBMIT to GuideMessage(
            ctx.getString(R.string.guide_step12_title),
            ctx.getString(R.string.guide_step12_msg),
            ctx.getString(R.string.guide_step12_tip),
            ctx.getString(R.string.guide_step12_action)
        )
    )

    // ── Public API ────────────────────────────────────────────────────────────

    fun isEnabled(ctx: Context): Boolean {
        return prefs(ctx).getBoolean(KEY_ENABLED, true)
    }

    fun getSubmittedSurveysCount(ctx: Context): Int =
        prefs(ctx).getInt(KEY_SURVEYS_SUBMITTED, 0)

    /**
     * Call this every time a survey is successfully submitted.
     * Resets the guide steps so user gets guided through the next survey too.
     */
    fun onSurveySubmitted(ctx: Context) {
        val count = getSubmittedSurveysCount(ctx) + 1
        prefs(ctx).edit()
            .putInt(KEY_SURVEYS_SUBMITTED, count)
            .putInt(KEY_STEP, 0)          // reset to step 0 for next survey
            .putBoolean(KEY_DONE, false)  // re-enable guide walkthrough
            .apply()
    }

    fun isDone(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_DONE, false)

    fun currentStep(ctx: Context): Step =
        Step.fromIndex(prefs(ctx).getInt(KEY_STEP, 0))

    fun enable(ctx: Context) {
        prefs(ctx).edit()
            .putBoolean(KEY_ENABLED, true)
            .putBoolean(KEY_DONE, false)
            .putInt(KEY_STEP, 0)
            .apply()
    }

    fun disable(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, false).apply()
    }

    fun advance(ctx: Context) {
        val next = currentStep(ctx).index + 1
        if (next > Step.SUBMIT.index) {
            prefs(ctx).edit().putBoolean(KEY_DONE, true).apply()
        } else {
            prefs(ctx).edit().putInt(KEY_STEP, next).apply()
        }
    }

    /** Jump directly to a specific step — use when a screen is reached by skipping others */
    fun jumpToStep(ctx: Context, step: Step) {
        prefs(ctx).edit()
            .putInt(KEY_STEP, step.index)
            .putBoolean(KEY_DONE, false)
            .apply()
    }

    /**
     * Show the guide card for the given screen step.
     * Only shows if guide is enabled AND user hasn't passed this step yet.
     * All text is loaded from strings.xml in the current user language.
     */
    fun show(
        activity: android.app.Activity,
        screenStep: Step,
        onAction: (() -> Unit)? = null
    ) {
        val ctx = activity
        if (!isEnabled(ctx) || isDone(ctx)) return

        val curStep = currentStep(ctx)
        if (curStep != screenStep) return

        val messages = buildMessages(ctx)
        val msg = messages[screenStep] ?: return

        dismiss(activity)

        val root = activity.window.decorView as? android.widget.FrameLayout ?: return
        val overlay = activity.layoutInflater
            .inflate(R.layout.overlay_ai_guide, root, false)

        overlay.tag = "ai_guide_overlay"

        overlay.findViewById<android.widget.TextView>(R.id.tv_guide_title).text  = msg.title
        overlay.findViewById<android.widget.TextView>(R.id.tv_guide_message).text = msg.message

        val tvTip = overlay.findViewById<android.widget.TextView>(R.id.tv_guide_tip)
        if (!msg.tip.isNullOrEmpty()) {
            tvTip.visibility = android.view.View.VISIBLE
            tvTip.text = msg.tip
        } else {
            tvTip.visibility = android.view.View.GONE
        }

        val btnAction = overlay.findViewById<android.widget.Button>(R.id.btn_guide_action)
        btnAction.text = msg.actionLabel.ifEmpty { ctx.getString(R.string.guide_got_it) }
        btnAction.setOnClickListener {
            advance(ctx)
            root.removeView(overlay)
            onAction?.invoke()
        }

        val btnClose = overlay.findViewById<android.widget.ImageButton>(R.id.btn_guide_close)
        btnClose.setOnClickListener {
            root.removeView(overlay)
        }

        val btnDisable = overlay.findViewById<android.widget.TextView>(R.id.tv_guide_disable)
        btnDisable.text = ctx.getString(R.string.guide_turn_off)
        btnDisable.setOnClickListener {
            AlertDialog.Builder(ctx)
                .setTitle(ctx.getString(R.string.guide_turn_off_title))
                .setMessage(ctx.getString(R.string.guide_turn_off_msg))
                .setPositiveButton(ctx.getString(R.string.guide_turn_off_confirm)) { _, _ ->
                    disable(ctx)
                    root.removeView(overlay)
                }
                .setNegativeButton(ctx.getString(R.string.guide_keep_on), null)
                .show()
        }

        // Progress text — translated: "Step 3 / 13" or "चरण 3 / 13"
        overlay.findViewById<android.widget.TextView>(R.id.tv_guide_progress).text =
            ctx.getString(R.string.guide_step_progress, screenStep.index + 1, Step.values().size)

        root.addView(overlay)
    }

    fun dismiss(activity: android.app.Activity) {
        val root = activity.window.decorView as? android.widget.FrameLayout ?: return
        root.findViewWithTag<android.view.View>("ai_guide_overlay")?.let { root.removeView(it) }
    }

    /** Called from Dashboard to show the guide chooser dialog */
    fun showGuideMenu(activity: android.app.Activity) {
        val ctx = activity
        val isDone = isDone(ctx)
        val step = currentStep(ctx)

        val options = arrayOf(
            ctx.getString(R.string.guide_option_rules),
            if (isDone) ctx.getString(R.string.guide_option_restart)
            else ctx.getString(R.string.guide_option_resume),
            ctx.getString(R.string.guide_option_turnoff)
        )

        AlertDialog.Builder(ctx)
            .setTitle(ctx.getString(R.string.guide_menu_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> OnboardingGuideActivity.resetAndShow(activity)
                    1 -> {
                        enable(ctx)
                        show(activity, Step.DASHBOARD)
                    }
                    2 -> {
                        disable(ctx)
                        Toast.makeText(ctx, ctx.getString(R.string.guide_disabled_toast), Toast.LENGTH_LONG).show()
                    }
                }
            }
            .show()
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREF_GUIDE, Context.MODE_PRIVATE)
}
