package com.cropsurvey.app.guide

import android.content.Context
import android.graphics.Color
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.cropsurvey.app.R

/**
 * AiGuideOverlay
 *
 * Lightweight, fully offline AI guide system.
 * Displays contextual tips as a persistent "coach card" at the bottom of the screen.
 * The guide tracks which step the user is on via SharedPreferences and advances
 * automatically when the user performs the right action.
 *
 * Guide journey (12 steps):
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
 *   When the user completes a step:
 *     AiGuideOverlay.advance(context)
 */
object AiGuideOverlay {

    private const val PREF_GUIDE  = "crop_survey_ai_guide"
    private const val KEY_STEP    = "ai_guide_step"
    private const val KEY_ENABLED = "ai_guide_enabled"
    private const val KEY_DONE    = "ai_guide_done"

    // ── Step definitions ───────────────────────────────────────────────────────
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
        val actionLabel: String = "Got it →"
    )

    private val MESSAGES = mapOf(
        Step.DASHBOARD to GuideMessage(
            "👋 Welcome! I'm your AI Survey Guide",
            "I'll walk you through completing your first survey from start to finish.\n\nYou can see 3 survey types on this screen: CLS, CHM, and CCE.",
            "💡 Tip: CLS = Crop Loss Survey. Start with CLS if this is your first time.",
            "Start with CLS →"
        ),
        Step.SURVEY_CARD_SELECT to GuideMessage(
            "📋 Choose a Survey Type",
            "Tap the CLS card to start a Crop Loss Survey.\n\n• CLS: For documenting crop damage\n• CHM: For monitoring crop health (up to 5 visits)\n• CCE: For crop cutting experiments",
            "💡 Tip: All 3 types follow the same flow — form + photos + submit."
        ),
        Step.FARMER_VERIFICATION to GuideMessage(
            "👨‍🌾 Farmer Verification",
            "You can verify the farmer's identity using their mobile number and OTP.\n\nThis is optional — tap SKIP if the farmer is not present right now.",
            "💡 Tip: Verified surveys are processed faster and have higher approval rates."
        ),
        Step.FARMER_VERIFIED to GuideMessage(
            "✅ Great! Farmer Verified",
            "The farmer's phone is confirmed. This will appear as a green banner inside the survey form.",
            "💡 Tip: Even if skipped now, you can always verify before submitting."
        ),
        Step.MAP_DRAW to GuideMessage(
            "🗺️ Draw the Field Boundary",
            "Tap on the map to add at least 3 points around the field boundary. The app will calculate the field area automatically.\n\nBe at the actual field location — GPS accuracy matters!",
            "💡 Tip: Tap DELETE to start over if you make a mistake."
        ),
        Step.FORM_OPEN to GuideMessage(
            "📝 Fill the Survey Form",
            "The form has multiple sections — tap any section header to expand it. You must fill all required fields (marked with *).\n\nYour progress is auto-saved every 15 seconds.",
            "💡 Tip: Tap 'Save Draft' anytime to manually save and come back later."
        ),
        Step.FORM_BASIC_INFO to GuideMessage(
            "📅 Basic Information Section",
            "Fill in:\n• Year (e.g. 2025-2026)\n• Season (Kharif / Rabi / Zaid)\n• Scheme (PMFBY, RWBCIS, etc.)\n\nAll dropdowns are translated to your chosen language.",
            "💡 Tip: The year auto-fills based on the current date."
        ),
        Step.FORM_LOCATION to GuideMessage(
            "📍 Location Section",
            "Select your State → District → Tehsil → Village.\n\nEach dropdown loads based on the previous selection. Enter the Khasra number from the land records.",
            "💡 Tip: If your village is missing, enter it manually in the Gram Panchayat field."
        ),
        Step.FORM_CROP_DETAILS to GuideMessage(
            "🌾 Crop Details Section",
            "Select the crop type, variety, stage, and irrigation type.\n\nFor CLS: also fill Loss %, Cause of Event, and Intimation Date.",
            "💡 Tip: Crop stage matters for verification — select the stage the crop is CURRENTLY in."
        ),
        Step.PHOTOS_TAB to GuideMessage(
            "📸 Now for the Photos",
            "Tap the PHOTOS tab at the top. You'll see a list of photo slots — some are Required ✦ and some are Optional.\n\nYou must capture ALL required photos before you can submit.",
            "💡 Tip: Photos are geo-tagged automatically. No need to add location manually."
        ),
        Step.PHOTOS_CAPTURE to GuideMessage(
            "📷 Capture Required Photos",
            "Tap CAPTURE next to each photo slot. Hold the phone steady and ensure good lighting.\n\nFollow the label — e.g. 'Field North', 'Nadir View' (phone directly above crop).",
            "💡 Tip: If a photo is blurry, tap RETAKE immediately."
        ),
        Step.SAVE_DRAFT to GuideMessage(
            "💾 Save Your Draft",
            "Tap SAVE DRAFT at the bottom to sync your work to the server. This lets you:\n• Continue later\n• Switch devices (after admin unlock)\n• Prevent data loss\n\nYour draft will appear on the Dashboard under the Draft tab.",
            "💡 Tip: The app auto-saves every 15 seconds, but manual save is more reliable on poor networks."
        ),
        Step.SUBMIT to GuideMessage(
            "🎉 Ready to Submit!",
            "Everything is filled. Tap SUBMIT → to send your survey for QC review.\n\nAfter submission:\n• Status changes to 'Submitted'\n• QC team reviews within 24-48 hours\n• You'll see Approved ✅ or Rejected ❌ on the dashboard\n\nIf rejected, you can edit and resubmit. You've completed your first survey!",
            "💡 You can always tap the 🤖 Guide button on the dashboard to re-read these tips.",
            "Submit & Finish Guide 🎉"
        )
    )

    // ── Public API ─────────────────────────────────────────────────────────────

    fun isEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ENABLED, true)

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

    /**
     * Show the guide card for the given screen step.
     * Only shows if the guide is enabled AND the user hasn't passed this step yet.
     *
     * @param activity the current activity (used to inflate and attach the overlay)
     * @param screenStep which screen the user is on right now
     * @param onAction optional callback fired when user taps the action button
     */
    fun show(
        activity: android.app.Activity,
        screenStep: Step,
        onAction: (() -> Unit)? = null
    ) {
        val ctx = activity
        if (!isEnabled(ctx) || isDone(ctx)) return

        val curStep = currentStep(ctx)
        // Only show the tip for the CURRENT step — not old ones
        if (curStep != screenStep) return

        val msg = MESSAGES[screenStep] ?: return

        // Remove any previous overlay
        dismiss(activity)

        val root = activity.window.decorView as? FrameLayout ?: return
        val overlay = activity.layoutInflater
            .inflate(R.layout.overlay_ai_guide, root, false)

        overlay.tag = "ai_guide_overlay"

        overlay.findViewById<TextView>(R.id.tv_guide_title).text  = msg.title
        overlay.findViewById<TextView>(R.id.tv_guide_message).text = msg.message

        val tvTip = overlay.findViewById<TextView>(R.id.tv_guide_tip)
        if (msg.tip != null) {
            tvTip.visibility = View.VISIBLE
            tvTip.text = msg.tip
        } else {
            tvTip.visibility = View.GONE
        }

        val btnAction = overlay.findViewById<Button>(R.id.btn_guide_action)
        btnAction.text = msg.actionLabel
        btnAction.setOnClickListener {
            advance(ctx)
            root.removeView(overlay)
            onAction?.invoke()
        }

        val btnClose = overlay.findViewById<ImageButton>(R.id.btn_guide_close)
        btnClose.setOnClickListener {
            root.removeView(overlay)
            // Don't advance — user just dismissed without acting
        }

        val btnDisable = overlay.findViewById<TextView>(R.id.tv_guide_disable)
        btnDisable.setOnClickListener {
            AlertDialog.Builder(ctx)
                .setTitle("Turn off AI Guide?")
                .setMessage("The guide will stop showing. You can re-enable it from Dashboard → Guide button.")
                .setPositiveButton("Turn Off") { _, _ ->
                    disable(ctx)
                    root.removeView(overlay)
                }
                .setNegativeButton("Keep On", null)
                .show()
        }

        // Progress text e.g. "Step 3 / 12"
        overlay.findViewById<TextView>(R.id.tv_guide_progress).text =
            "Step ${screenStep.index + 1} / ${Step.values().size}"

        root.addView(overlay)
    }

    fun dismiss(activity: android.app.Activity) {
        val root = activity.window.decorView as? FrameLayout ?: return
        root.findViewWithTag<View>("ai_guide_overlay")?.let { root.removeView(it) }
    }

    /** Called from Dashboard to show the full guide chooser dialog */
    fun showGuideMenu(activity: android.app.Activity) {
        val ctx = activity
        val isDone = isDone(ctx)
        val step = currentStep(ctx)

        val options = arrayOf(
            "📖 Show App Rules & Guidelines",
            if (isDone) "🔄 Restart Survey Guide" else "🤖 Resume Guide (Step ${step.index + 1}/${Step.values().size})",
            "❌ Turn Off AI Guide"
        )

        AlertDialog.Builder(ctx)
            .setTitle("🤖 AI Survey Guide")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> OnboardingGuideActivity.resetAndShow(activity)
                    1 -> {
                        enable(ctx)
                        show(activity, Step.DASHBOARD)
                    }
                    2 -> {
                        disable(ctx)
                        Toast.makeText(ctx, "Guide turned off. Tap 🤖 to turn it back on.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .show()
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREF_GUIDE, Context.MODE_PRIVATE)
}
