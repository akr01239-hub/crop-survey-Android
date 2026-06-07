package com.cropsurvey.app.survey

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.cropsurvey.app.BaseActivity
import com.cropsurvey.app.utils.SurveySession
import com.cropsurvey.app.R
import com.cropsurvey.app.guide.AiGuideOverlay
import com.cropsurvey.app.map.PolygonMapActivity
import com.cropsurvey.app.network.ApiClient
import com.cropsurvey.app.survey.chm.ChmVisitActivity
import kotlinx.coroutines.launch
import org.json.JSONObject

class FarmerVerificationActivity : BaseActivity() {

    // ── Views ─────────────────────────────────────────────────────────────────
    private lateinit var layoutStep1: View
    private lateinit var layoutStep2: View
    private lateinit var layoutStep3: View

    private lateinit var btnYes: Button
    private lateinit var btnNo: Button

    private lateinit var etPhone: EditText
    private lateinit var btnSendOtp: Button
    private lateinit var tvPhoneError: TextView
    private lateinit var progressSend: ProgressBar

    private lateinit var tvOtpSentTo: TextView
    private lateinit var etOtp: EditText
    private lateinit var btnVerifyOtp: Button
    private lateinit var btnResend: TextView
    private lateinit var tvResendTimer: TextView
    private lateinit var progressVerify: ProgressBar
    private lateinit var tvOtpError: TextView

    private var resendTimer: CountDownTimer? = null
    private lateinit var surveyType: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_farmer_verification)

        surveyType = intent.getStringExtra("survey_type") ?: "CLS"

        bindViews()
        showStep(1)
        setupStep1()
        setupStep2()
        setupStep3()

        // Show guide AFTER layout is fully drawn so overlay attaches correctly
        window.decorView.post {
            AiGuideOverlay.show(this, AiGuideOverlay.Step.FARMER_VERIFICATION)
        }
    }

    private fun bindViews() {
        layoutStep1      = findViewById(R.id.layout_step1)
        layoutStep2      = findViewById(R.id.layout_step2)
        layoutStep3      = findViewById(R.id.layout_step3)
        btnYes           = findViewById(R.id.btn_yes)
        btnNo            = findViewById(R.id.btn_no)
        etPhone          = findViewById(R.id.et_farmer_phone)
        btnSendOtp       = findViewById(R.id.btn_send_otp)
        tvPhoneError     = findViewById(R.id.tv_phone_error)
        progressSend     = findViewById(R.id.progress_send)
        tvOtpSentTo      = findViewById(R.id.tv_otp_sent_to)
        etOtp            = findViewById(R.id.et_otp)
        btnVerifyOtp     = findViewById(R.id.btn_verify_otp)
        btnResend        = findViewById(R.id.btn_resend)
        tvResendTimer    = findViewById(R.id.tv_resend_timer)
        progressVerify   = findViewById(R.id.progress_verify)
        tvOtpError       = findViewById(R.id.tv_otp_error)
    }

    private fun setupStep1() {
        btnYes.setOnClickListener { showStep(2) }
        btnNo.setOnClickListener {
            AiGuideOverlay.advance(this)  // skip verification → advance past FARMER_VERIFICATION step
            proceedToSurvey(verified = false, farmerPhone = null)
        }
    }

    private fun setupStep2() {
        btnSendOtp.setOnClickListener {
            val phone = etPhone.text.toString().trim()
            if (phone.length != 10 || !phone.all { it.isDigit() }) {
                tvPhoneError.text = "Please enter a valid 10-digit mobile number"
                tvPhoneError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            tvPhoneError.visibility = View.GONE
            sendOtp(phone)
        }
    }

    private fun setupStep3() {
        btnVerifyOtp.setOnClickListener {
            val otp = etOtp.text.toString().trim()
            if (otp.length != 6) {
                tvOtpError.text = "Enter the 6-digit OTP"
                tvOtpError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            tvOtpError.visibility = View.GONE
            verifyOtp(otp)
        }

        btnResend.setOnClickListener {
            val phone = etPhone.text.toString().trim()
            if (phone.isNotEmpty()) sendOtp(phone)
        }
    }

    // ── Send OTP via backend → Fast2SMS ───────────────────────────────────────
    private fun sendOtp(phone: String) {
        setStep2Loading(true)
        lifecycleScope.launch {
            try {
                val res = ApiClient.service.sendFarmerOtp(
                    mapOf("phone" to phone)
                )
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    if (body["success"] == true) {
                        setStep2Loading(false)
                        tvOtpSentTo.text = "OTP sent to +91-$phone"
                        layoutStep3.visibility = View.VISIBLE
                        startResendTimer()
                    } else {
                        setStep2Loading(false)
                        val msg = body["error"]?.toString() ?: "Failed to send OTP"
                        tvPhoneError.text = msg
                        tvPhoneError.visibility = View.VISIBLE
                    }
                } else {
                    setStep2Loading(false)
                    tvPhoneError.text = "Failed to send OTP. Check number and try again."
                    tvPhoneError.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                setStep2Loading(false)
                tvPhoneError.text = "Network error. Please try again."
                tvPhoneError.visibility = View.VISIBLE
            }
        }
    }

    // ── Verify OTP via backend ────────────────────────────────────────────────
    private fun verifyOtp(otp: String) {
        val phone = etPhone.text.toString().trim()
        setStep3Loading(true)
        lifecycleScope.launch {
            try {
                val res = ApiClient.service.verifyFarmerOtp(
                    mapOf("phone" to phone, "otp" to otp)
                )
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    if (body["success"] == true) {
                        setStep3Loading(false)
                        showSuccessAndProceed(phone)
                    } else {
                        setStep3Loading(false)
                        tvOtpError.text = "Invalid OTP. Please try again."
                        tvOtpError.visibility = View.VISIBLE
                    }
                } else {
                    setStep3Loading(false)
                    tvOtpError.text = "Invalid OTP. Please try again."
                    tvOtpError.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                setStep3Loading(false)
                tvOtpError.text = "Network error. Please try again."
                tvOtpError.visibility = View.VISIBLE
            }
        }
    }

    private fun showSuccessAndProceed(farmerPhone: String) {
        AiGuideOverlay.advance(this)  // advance past FARMER_VERIFICATION
        AiGuideOverlay.show(this, AiGuideOverlay.Step.FARMER_VERIFIED)
        AlertDialog.Builder(this)
            .setTitle("✅ Farmer Verified!")
            .setMessage("Phone number +91-$farmerPhone has been successfully verified.\n\nProceeding to survey...")
            .setCancelable(false)
            .setPositiveButton("Start Survey") { _, _ ->
                AiGuideOverlay.advance(this)  // advance past FARMER_VERIFIED
                proceedToSurvey(verified = true, farmerPhone = farmerPhone)
            }
            .show()
    }

    private fun proceedToSurvey(verified: Boolean, farmerPhone: String?) {
        // Store directly in SurveySession so it survives the entire activity chain
        // (FarmerVerification → ChmVisit/PolygonMap → SurveyForm)
        if (verified && !farmerPhone.isNullOrEmpty()) {
            SurveySession.formData["farmer_verified"] = "yes"
            SurveySession.formData["farmer_phone"]    = farmerPhone
        } else {
            SurveySession.formData["farmer_verified"] = "skipped"
            SurveySession.formData["farmer_phone"]    = ""
        }

        val intent = if (surveyType == "CHM") {
            Intent(this, ChmVisitActivity::class.java)
        } else {
            Intent(this, PolygonMapActivity::class.java)
        }
        intent.putExtra("survey_type", surveyType)
        intent.putExtra("farmer_verified", verified)
        intent.putExtra("farmer_phone", farmerPhone ?: "")
        startActivity(intent)
        finish()
    }

    private fun startResendTimer() {
        btnResend.visibility = View.GONE
        tvResendTimer.visibility = View.VISIBLE
        resendTimer?.cancel()
        resendTimer = object : CountDownTimer(30000, 1000) {
            override fun onTick(ms: Long) {
                tvResendTimer.text = "Resend OTP in ${ms / 1000}s"
            }
            override fun onFinish() {
                tvResendTimer.visibility = View.GONE
                btnResend.visibility = View.VISIBLE
            }
        }.start()
    }

    private fun showStep(step: Int) {
        // Progressive reveal — each step stays visible, next appears below
        layoutStep1.visibility = View.VISIBLE  // always visible
        layoutStep2.visibility = if (step >= 2) View.VISIBLE else View.GONE
        layoutStep3.visibility = if (step >= 3) View.VISIBLE else View.GONE
    }

    private fun setStep2Loading(loading: Boolean) {
        progressSend.visibility = if (loading) View.VISIBLE else View.GONE
        btnSendOtp.isEnabled = !loading
        btnSendOtp.text = if (loading) "Sending..." else "Send OTP"
        etPhone.isEnabled = !loading
    }

    private fun setStep3Loading(loading: Boolean) {
        progressVerify.visibility = if (loading) View.VISIBLE else View.GONE
        btnVerifyOtp.isEnabled = !loading
        btnVerifyOtp.text = if (loading) "Verifying..." else "Verify & Start Survey"
        etOtp.isEnabled = !loading
    }

    override fun onDestroy() {
        super.onDestroy()
        resendTimer?.cancel()
    }

    override fun onBackPressed() {
        when {
            layoutStep3.visibility == View.VISIBLE -> showStep(2)
            layoutStep2.visibility == View.VISIBLE -> showStep(1)
            else -> super.onBackPressed()
        }
    }
}