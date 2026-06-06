package com.cropsurvey.app.auth

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.cropsurvey.app.BaseActivity
import androidx.lifecycle.lifecycleScope
import com.cropsurvey.app.R
import com.cropsurvey.app.network.ApiClient
import kotlinx.coroutines.launch

class ForgotPasswordActivity : BaseActivity() {

    // Step 1 — Phone
    private lateinit var layoutPhoneStep: View
    private lateinit var etPhone: EditText
    private lateinit var btnNextPhone: Button

    // Step 2 — PAN verification
    private lateinit var layoutPanStep: View
    private lateinit var tvPanHint: TextView
    private lateinit var etPan: EditText
    private lateinit var btnVerifyPan: Button

    // Step 3 — New password
    private lateinit var layoutPasswordStep: View
    private lateinit var etNewPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnShowPassword: TextView
    private lateinit var btnResetPassword: Button

    // Step dots
    private lateinit var tvStep1Dot: TextView
    private lateinit var tvStep2Dot: TextView
    private lateinit var tvStep3Dot: TextView
    private lateinit var stepLine1: View
    private lateinit var stepLine2: View

    private lateinit var btnBack: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView

    private var currentPhone = ""
    private var passwordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)
        bindViews()
        setupUI()
        showStep(1)
    }

    private fun bindViews() {
        layoutPhoneStep    = findViewById(R.id.layout_phone_step)
        etPhone            = findViewById(R.id.et_phone)
        btnNextPhone       = findViewById(R.id.btn_next_phone)

        layoutPanStep      = findViewById(R.id.layout_pan_step)
        tvPanHint          = findViewById(R.id.tv_pan_hint)
        etPan              = findViewById(R.id.et_pan)
        btnVerifyPan       = findViewById(R.id.btn_verify_pan)

        layoutPasswordStep = findViewById(R.id.layout_password_step)
        etNewPassword      = findViewById(R.id.et_new_password)
        etConfirmPassword  = findViewById(R.id.et_confirm_password)
        btnShowPassword    = findViewById(R.id.btn_show_password)
        btnResetPassword   = findViewById(R.id.btn_reset_password)

        tvStep1Dot  = findViewById(R.id.tv_step1_dot)
        tvStep2Dot  = findViewById(R.id.tv_step2_dot)
        tvStep3Dot  = findViewById(R.id.tv_step3_dot)
        stepLine1   = findViewById(R.id.step_line_1)
        stepLine2   = findViewById(R.id.step_line_2)

        btnBack     = findViewById(R.id.btn_back)
        progressBar = findViewById(R.id.progress_bar)
        tvError     = findViewById(R.id.tv_error)
    }

    private fun setupUI() {
        btnBack.setOnClickListener {
            // If not on step 1, go back a step instead of finishing
            when {
                layoutPanStep.visibility == View.VISIBLE      -> showStep(1)
                layoutPasswordStep.visibility == View.VISIBLE -> showStep(2)
                else                                           -> finish()
            }
        }

        btnNextPhone.setOnClickListener {
            val phone = etPhone.text.toString().trim()
            if (phone.length < 10) { showError("Enter a valid 10-digit phone number"); return@setOnClickListener }
            currentPhone = if (phone.startsWith("+91")) phone else "+91$phone"
            checkPhoneExists()
        }

        btnVerifyPan.setOnClickListener {
            val pan = etPan.text.toString().trim().uppercase()
            if (pan.length != 10) { showError("PAN number must be 10 characters"); return@setOnClickListener }
            if (!pan.matches(Regex("[A-Z]{5}[0-9]{4}[A-Z]"))) {
                showError("Invalid PAN format (e.g. ABCDE1234F)")
                return@setOnClickListener
            }
            verifyPan(pan)
        }

        btnShowPassword.setOnClickListener {
            passwordVisible = !passwordVisible
            val type = if (passwordVisible)
                android.text.InputType.TYPE_CLASS_TEXT
            else
                android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            etNewPassword.inputType = type
            etConfirmPassword.inputType = type
            // Keep cursor at end
            etNewPassword.setSelection(etNewPassword.text.length)
            btnShowPassword.text = if (passwordVisible) "Hide" else "Show"
        }

        btnResetPassword.setOnClickListener {
            val newPass = etNewPassword.text.toString()
            val confirm = etConfirmPassword.text.toString()
            if (newPass.length < 6) { showError("Password must be at least 6 characters"); return@setOnClickListener }
            if (newPass != confirm) { showError("Passwords do not match"); return@setOnClickListener }
            resetPassword(newPass)
        }
    }

    // ── Step 1: Just check the phone exists in the system ──────────────────
    private fun checkPhoneExists() {
        setLoading(true); clearError()
        lifecycleScope.launch {
            try {
                // Strip + prefix for GET query param — Retrofit encodes + as %2B but servers may decode as space
                val res = ApiClient.service.checkPhoneExists(currentPhone.removePrefix("+"))
                if (res.isSuccessful) {
                    showStep(2)
                    tvPanHint.text = "Enter the PAN number linked to $currentPhone"
                } else {
                    showError("No account found for this phone number.")
                }
            } catch (e: Exception) {
                showError("Network error. Please try again.")
            } finally {
                setLoading(false)
            }
        }
    }

    // ── Step 2: Verify PAN against the server ──────────────────────────────
    private fun verifyPan(pan: String) {
        setLoading(true); clearError()
        lifecycleScope.launch {
            try {
                val res = ApiClient.service.verifyPanForReset(
                    VerifyPanRequest(phone = currentPhone, pan_no = pan)
                )
                if (res.isSuccessful) {
                    showStep(3)
                } else {
                    showError("PAN number does not match our records. Please check and try again.")
                }
            } catch (e: Exception) {
                showError("Verification failed. Please try again.")
            } finally {
                setLoading(false)
            }
        }
    }

    // ── Step 3: Reset password (phone + PAN already verified server-side) ──
    private fun resetPassword(newPassword: String) {
        setLoading(true); clearError()
        lifecycleScope.launch {
            try {
                val res = ApiClient.service.resetPasswordByPan(
                    ResetPasswordPanRequest(
                        phone        = currentPhone,
                        pan_no       = etPan.text.toString().trim().uppercase(),
                        new_password = newPassword
                    )
                )
                if (res.isSuccessful) {
                    Toast.makeText(
                        this@ForgotPasswordActivity,
                        "✅ Password reset successfully! Please login.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                } else {
                    showError("Failed to reset password. Please start over.")
                }
            } catch (e: Exception) {
                showError("Network error. Please try again.")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun showStep(step: Int) {
        layoutPhoneStep.visibility    = if (step == 1) View.VISIBLE else View.GONE
        layoutPanStep.visibility      = if (step == 2) View.VISIBLE else View.GONE
        layoutPasswordStep.visibility = if (step == 3) View.VISIBLE else View.GONE
        clearError()
        updateStepDots(step)
    }

    private fun updateStepDots(step: Int) {
        val activeColor   = getColor(android.R.color.holo_blue_dark)
        val inactiveColor = getColor(android.R.color.darker_gray)
        val doneColor     = getColor(android.R.color.holo_green_dark)
        val white         = getColor(android.R.color.white)

        fun setDot(tv: TextView, state: String) { // "active", "done", "inactive"
            tv.setTextColor(white)
            when (state) {
                "done"     -> tv.backgroundTintList = android.content.res.ColorStateList.valueOf(doneColor)
                "active"   -> tv.backgroundTintList = android.content.res.ColorStateList.valueOf(activeColor)
                else       -> {
                    tv.setTextColor(inactiveColor)
                    tv.backgroundTintList = android.content.res.ColorStateList.valueOf(
                        getColor(android.R.color.transparent))
                }
            }
        }

        when (step) {
            1 -> { setDot(tvStep1Dot, "active"); setDot(tvStep2Dot, "inactive"); setDot(tvStep3Dot, "inactive") }
            2 -> { setDot(tvStep1Dot, "done");   setDot(tvStep2Dot, "active");   setDot(tvStep3Dot, "inactive") }
            3 -> { setDot(tvStep1Dot, "done");   setDot(tvStep2Dot, "done");     setDot(tvStep3Dot, "active")   }
        }

        val doneLineColor = android.content.res.ColorStateList.valueOf(doneColor)
        val idleLineColor = android.content.res.ColorStateList.valueOf(inactiveColor)
        stepLine1.backgroundTintList = if (step >= 2) doneLineColor else idleLineColor
        stepLine2.backgroundTintList = if (step >= 3) doneLineColor else idleLineColor
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility  = if (loading) View.VISIBLE else View.GONE
        btnNextPhone.isEnabled  = !loading
        btnVerifyPan.isEnabled  = !loading
        btnResetPassword.isEnabled = !loading
    }

    private fun showError(msg: String) { tvError.text = msg; tvError.visibility = View.VISIBLE }
    private fun clearError() { tvError.visibility = View.GONE }
}

data class VerifyPanRequest(val phone: String, val pan_no: String)
data class ResetPasswordPanRequest(val phone: String, val pan_no: String, val new_password: String)