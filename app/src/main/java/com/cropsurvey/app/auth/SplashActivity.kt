package com.cropsurvey.app.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.cropsurvey.app.BaseActivity
import com.cropsurvey.app.R
import com.cropsurvey.app.dashboard.DashboardActivity
import com.cropsurvey.app.i18n.LanguageManager
import com.cropsurvey.app.utils.MockLocationDetector
import com.cropsurvey.app.utils.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // ── MOCK LOCATION CHECK ───────────────────────────────────────────────
        if (MockLocationDetector.isMockLocation(this)) {
            MockLocationDetector.showCrashDialogAndKill(this)
            return
        }

        val ivLogo    = findViewById<ImageView>(R.id.iv_splash_logo)
        val tvAppName = findViewById<TextView>(R.id.tv_splash_name)
        val tvTagline = findViewById<TextView>(R.id.tv_splash_tagline)

        // Apply translated tagline immediately
        tvTagline.text = getString(R.string.app_tagline)

        val logoAnim = AnimationUtils.loadAnimation(this, R.anim.splash_logo_in)
        ivLogo.startAnimation(logoAnim)

        lifecycleScope.launch {
            delay(300)
            val textAnim = AnimationUtils.loadAnimation(this@SplashActivity, R.anim.splash_text_in)
            tvAppName.startAnimation(textAnim)
            tvAppName.visibility = android.view.View.VISIBLE

            delay(150)
            val tagAnim = AnimationUtils.loadAnimation(this@SplashActivity, R.anim.splash_text_in)
            tvTagline.startAnimation(tagAnim)
            tvTagline.visibility = android.view.View.VISIBLE

            delay(1800)
            navigate()
        }
    }

    private fun navigate() {
        val dest = when {
            // Already logged in → go to dashboard
            SessionManager.isLoggedIn() -> DashboardActivity::class.java

            // Language was NEVER set (first launch) → show language selection
            isFirstLaunch() -> LanguageSelectionActivity::class.java

            // Language set, not logged in → go to login
            else -> LoginActivity::class.java
        }

        startActivity(Intent(this, dest))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    /**
     * Returns true only on the very first app launch before any language was chosen.
     * After that, language is always persisted in SharedPreferences.
     */
    private fun isFirstLaunch(): Boolean {
        val prefs = getSharedPreferences("crop_survey_language", MODE_PRIVATE)
        return !prefs.contains("selected_language")
    }
}
