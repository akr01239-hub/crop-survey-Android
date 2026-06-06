package com.cropsurvey.app

import android.app.Application
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.cropsurvey.app.i18n.LanguageManager
import com.cropsurvey.app.network.ApiClient
import com.cropsurvey.app.utils.SessionManager
import com.cropsurvey.app.utils.SurveySession
import kotlin.system.exitProcess

class CropSurveyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // ── 1. Initialize language FIRST — must be before any UI is created ───
        LanguageManager.init(this)

        // ── 2. Initialize session and survey state ────────────────────────────
        SessionManager.init(this)
        SurveySession.init(this)

        // ── 3. Set device ID globally ─────────────────────────────────────────
        ApiClient.deviceId = Settings.Secure.getString(
            contentResolver, Settings.Secure.ANDROID_ID
        )

        // ── 4. Global security violation handler ──────────────────────────────
        ApiClient.onSecurityViolation = { code, message ->
            Handler(Looper.getMainLooper()).post {
                SessionManager.clearSession()

                val title = when (code) {
                    "DEVICE_LOCKED"   -> getString(R.string.device_locked_title)
                    "MOCK_LOCATION"   -> getString(R.string.mock_location_title)
                    "ACCOUNT_BLOCKED" -> getString(R.string.account_blocked_title)
                    else              -> getString(R.string.security_alert_title)
                }

                val activity = currentActivity
                if (activity != null && !activity.isFinishing) {
                    AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle(title)
                        .setMessage(message)
                        .setCancelable(false)
                        .setPositiveButton(getString(R.string.btn_ok)) { _, _ ->
                            activity.finishAffinity()
                            android.os.Process.killProcess(android.os.Process.myPid())
                            exitProcess(1)
                        }
                        .create()
                        .also { it.setCanceledOnTouchOutside(false) }
                        .show()
                } else {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    exitProcess(1)
                }
            }
        }
    }

    private var currentActivity: android.app.Activity? = null

    override fun onTerminate() {
        super.onTerminate()
        currentActivity = null
    }

    fun setCurrentActivity(activity: android.app.Activity?) {
        currentActivity = activity
    }
}
