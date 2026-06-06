package com.cropsurvey.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.cropsurvey.app.i18n.LanguageManager
import com.cropsurvey.app.settings.LanguageSettingsActivity

open class BaseActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_LANGUAGE_CHANGE = 9901
    }

    // Track the language code that was active when this activity was created
    private var activityLanguageCode: String = ""

    override fun attachBaseContext(newBase: Context) {
        val localizedContext = LanguageManager.applyLocale(newBase)
        super.attachBaseContext(localizedContext)
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        activityLanguageCode = LanguageManager.getSelectedLanguageCode()
    }

    override fun onResume() {
        super.onResume()
        (application as? CropSurveyApp)?.setCurrentActivity(this)
        // If language changed while this activity was in the back stack, recreate it
        val currentCode = LanguageManager.getSelectedLanguageCode()
        if (activityLanguageCode.isNotEmpty() && activityLanguageCode != currentCode) {
            activityLanguageCode = currentCode
            recreate()
        }
    }

    override fun onPause() {
        super.onPause()
        (application as? CropSurveyApp)?.setCurrentActivity(null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_LANGUAGE_CHANGE && resultCode == RESULT_OK) {
            // Language changed — recreate this activity so strings update in place
            recreate()
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        injectLanguageButton()
    }

    /**
     * Injects a floating 🌐 language button into the top-right corner of every screen.
     * Uses the root decorView so it overlays on top of any layout without modifying XMLs.
     */
    private fun injectLanguageButton() {
        // Skip screens where language button would be redundant or disruptive
        val skipClasses = listOf(
            "LanguageSelectionActivity",
            "LanguageSettingsActivity",
            "SplashActivity",
            "PhotoCaptureActivity"
        )
        if (skipClasses.any { this.javaClass.simpleName == it }) return

        val decorView = window.decorView as? FrameLayout ?: return

        // Remove any existing injected button to avoid duplicates on recreate
        decorView.findViewWithTag<View>("lang_fab")?.let { decorView.removeView(it) }

        val btn = ImageButton(this).apply {
            tag = "lang_fab"
            setImageResource(R.drawable.ic_language)
            setColorFilter(Color.WHITE)
            setBackgroundResource(R.drawable.bg_lang_fab)
            val size = (48 * resources.displayMetrics.density).toInt()
            val params = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.TOP or Gravity.END
                val margin = (12 * resources.displayMetrics.density).toInt()
                val topMargin = (44 * resources.displayMetrics.density).toInt()
                setMargins(margin, topMargin, margin, margin)
            }
            layoutParams = params
            contentDescription = getString(R.string.change_language)
            setOnClickListener {
                startActivityForResult(
                    Intent(this@BaseActivity, LanguageSettingsActivity::class.java),
                    REQUEST_LANGUAGE_CHANGE
                )
            }
        }
        decorView.addView(btn)
    }
}