package com.cropsurvey.app.auth

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.os.Build
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.cropsurvey.app.BaseActivity
import androidx.lifecycle.lifecycleScope
import com.cropsurvey.app.R
import com.cropsurvey.app.dashboard.DashboardActivity
import com.cropsurvey.app.models.User
import com.cropsurvey.app.network.ApiClient
import com.cropsurvey.app.settings.LanguageSettingsActivity
import com.cropsurvey.app.utils.GpsHelper
import com.cropsurvey.app.utils.MockLocationDetector
import com.cropsurvey.app.utils.SessionManager
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.http.Body
import retrofit2.http.POST

class LoginActivity : BaseActivity() {

    private lateinit var etPhone: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnShowPassword: TextView
    private lateinit var btnLogin: Button
    private lateinit var btnSignup: TextView
    private lateinit var btnForgotPassword: TextView
    private lateinit var btnChangeLanguage: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView

    private var passwordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── MOCK LOCATION CHECK ────────────────────────────────────────────────
        if (MockLocationDetector.isMockLocation(this)) {
            MockLocationDetector.showCrashDialogAndKill(this)
            return
        }

        // ── AUTO-LOGIN: only if token exists AND this is the registered device ─
        // Note: we intentionally do NOT auto-login if isCurrentDevice() fails,
        // even if token exists — a reinstall on a new device should go to login.
        if (SessionManager.isLoggedIn() && SessionManager.isCurrentDevice(this)) {
            goToDashboard()
            return
        }

        // Token exists but wrong device — clear it so login screen shows clean
        if (SessionManager.isLoggedIn()) {
            SessionManager.clearSession()
        }

        setContentView(R.layout.activity_login)
        bindViews()
        setupUI()
    }

    private fun bindViews() {
        etPhone           = findViewById(R.id.et_phone)
        etPassword        = findViewById(R.id.et_password)
        btnShowPassword   = findViewById(R.id.btn_show_password)
        btnLogin          = findViewById(R.id.btn_login)
        btnSignup         = findViewById(R.id.btn_signup)
        btnForgotPassword = findViewById(R.id.btn_forgot_password)
        btnChangeLanguage = findViewById(R.id.btn_change_language)
        progressBar       = findViewById(R.id.progress_bar)
        tvError           = findViewById(R.id.tv_error)
    }

    private fun setupUI() {
        // ── Language button ────────────────────────────────────────────────────
        btnChangeLanguage.setOnClickListener {
            startActivity(Intent(this, LanguageSettingsActivity::class.java))
        }

        btnShowPassword.setOnClickListener {
            passwordVisible = !passwordVisible
            etPassword.inputType = if (passwordVisible)
                android.text.InputType.TYPE_CLASS_TEXT
            else
                android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            etPassword.setSelection(etPassword.text.length)
            btnShowPassword.text = if (passwordVisible) "Hide" else "Show"
        }

        btnLogin.setOnClickListener {
            val phone    = etPhone.text.toString().trim()
            val password = etPassword.text.toString()

            if (phone.length < 10) { showError("Enter a valid 10-digit phone number"); return@setOnClickListener }
            if (password.length < 6) { showError("Password must be at least 6 characters"); return@setOnClickListener }

            login(phone, password)
        }

        btnSignup.setOnClickListener { startActivity(Intent(this, SignupActivity::class.java)) }
        btnForgotPassword.setOnClickListener { startActivity(Intent(this, ForgotPasswordActivity::class.java)) }
    }

    private fun login(phone: String, password: String) {
        setLoading(true)
        clearError()

        lifecycleScope.launch {
            try {
                val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                val formattedPhone = if (phone.startsWith("+91")) phone else "+91$phone"
                val isMock = MockLocationDetector.isMockLocation(this@LoginActivity)

                val res = ApiClient.service.loginWithPassword(
                    LoginRequest(
                        phone     = formattedPhone,
                        password  = password,
                        device_id = deviceId,
                        is_mock   = isMock
                    )
                )

                if (res.isSuccessful && res.body() != null) {
                    val body    = res.body()!!
                    val user    = body.user
                    val session = body.session

                    if (user == null || session == null) {
                        showError("Login failed. Please try again.")
                        return@launch
                    }

                    if (user.isActive == false) {
                        showError("Your account is pending admin approval. Please wait.")
                        return@launch
                    }

                    SessionManager.saveSession(
                        accessToken  = session.accessToken,
                        refreshToken = session.refreshToken,
                        user         = user,
                        deviceId     = deviceId
                    )

                    val coords = GpsHelper.getCurrentLocation(this@LoginActivity)
                    try {
                        ApiClient.service.sendLoginLog(
                            LoginLogRequest(
                                lat             = coords?.lat,
                                lon             = coords?.lon,
                                device_id       = deviceId,
                                is_mock         = false,
                                device_model    = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                                device_brand    = Build.BRAND,
                                android_version = Build.VERSION.RELEASE
                            )
                        )
                    } catch (e: Exception) { /* non-fatal */ }

                    goToDashboard()

                } else {
                    val errorBody = res.errorBody()?.string()
                    try {
                        val json = JSONObject(errorBody ?: "")
                        val code = json.optString("code", "")
                        val errorMsg = json.optString("error", "Invalid phone or password")

                        when (code) {
                            "DEVICE_LOCKED" -> showCrashDialog("🔒 Account Locked to Another Device", errorMsg)
                            "MOCK_LOCATION" -> showCrashDialog("⚠️ Fake Location Detected", errorMsg)
                            "ACCOUNT_BLOCKED" -> showCrashDialog("🚫 Account Blocked", errorMsg)
                            else -> showError(errorMsg)
                        }
                    } catch (e: Exception) {
                        showError("Invalid phone or password")
                    }
                }

            } catch (e: Exception) {
                showError("Network error. Check your connection.")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun showCrashDialog(title: String, message: String) {
        SessionManager.clearSession()
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ -> finish() }
            .create()
            .also { it.setCanceledOnTouchOutside(false) }
            .show()
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnLogin.isEnabled     = !loading
        btnLogin.text          = if (loading) "Logging in..." else "Login"
    }

    private fun showError(msg: String) {
        tvError.text       = msg
        tvError.visibility = View.VISIBLE
    }

    private fun clearError() {
        tvError.visibility = View.GONE
    }

    private fun goToDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }
}

// ─── Request Models ───────────────────────────────────────────────────────────

data class LoginRequest(
    val phone: String,
    val password: String,
    val device_id: String? = null,
    val is_mock: Boolean = false
)
data class LoginLogRequest(
    val lat: Double?,
    val lon: Double?,
    val device_id: String?,
    val is_mock: Boolean,
    val device_model: String? = null,
    val device_brand: String? = null,
    val android_version: String? = null
)