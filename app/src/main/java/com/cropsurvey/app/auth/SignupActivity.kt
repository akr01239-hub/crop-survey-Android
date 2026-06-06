package com.cropsurvey.app.auth

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.cropsurvey.app.BaseActivity
import androidx.lifecycle.lifecycleScope
import com.cropsurvey.app.R
import com.cropsurvey.app.network.ApiClient
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.launch
import org.json.JSONObject

class SignupActivity : BaseActivity() {

    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etEmail: EditText
    private lateinit var etAgency: EditText
    private lateinit var etEmployeeId: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnShowPassword: TextView
    private lateinit var btnSignup: Button
    private lateinit var btnBack: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var tvSuccess: View

    private var passwordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)
        bindViews()
        setupUI()
    }

    private fun bindViews() {
        etName            = findViewById(R.id.et_name)
        etPhone           = findViewById(R.id.et_phone)
        etEmail           = findViewById(R.id.et_email)
        etAgency          = findViewById(R.id.et_agency)
        etEmployeeId      = findViewById(R.id.et_employee_id)
        etPassword        = findViewById(R.id.et_password)
        etConfirmPassword = findViewById(R.id.et_confirm_password)
        btnShowPassword   = findViewById(R.id.btn_show_password)
        btnSignup         = findViewById(R.id.btn_signup)
        btnBack           = findViewById(R.id.btn_back)
        progressBar       = findViewById(R.id.progress_bar)
        tvError           = findViewById(R.id.tv_error)
        tvSuccess         = findViewById(R.id.layout_success)
    }

    private fun setupUI() {
        btnBack.setOnClickListener { finish() }

        btnShowPassword.setOnClickListener {
            passwordVisible = !passwordVisible
            val type = if (passwordVisible)
                android.text.InputType.TYPE_CLASS_TEXT
            else
                android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            etPassword.inputType = type
            etConfirmPassword.inputType = type
            etPassword.setSelection(etPassword.text.length)
            btnShowPassword.text = if (passwordVisible) "Hide" else "Show"
        }

        btnSignup.setOnClickListener {
            val name       = etName.text.toString().trim()
            val phone      = etPhone.text.toString().trim()
            val email      = etEmail.text.toString().trim()
            val agency     = etAgency.text.toString().trim()
            val employeeId = etEmployeeId.text.toString().trim()
            val password   = etPassword.text.toString()
            val confirm    = etConfirmPassword.text.toString()

            // Validation
            if (name.isEmpty()) { showError("Full name is required"); return@setOnClickListener }
            if (phone.length < 10) { showError("Enter a valid 10-digit phone number"); return@setOnClickListener }
            if (employeeId.isEmpty()) { showError("Employee ID is required"); return@setOnClickListener }
            if (employeeId.length < 3) { showError("Employee ID must be at least 3 characters"); return@setOnClickListener }
            if (password.length < 6) { showError("Password must be at least 6 characters"); return@setOnClickListener }
            if (password != confirm) { showError("Passwords do not match"); return@setOnClickListener }

            signup(name, phone, email, agency, employeeId, password)
        }
    }

    private fun signup(name: String, phone: String, email: String, agency: String, employeeId: String, password: String) {
        setLoading(true)
        clearError()

        val formattedPhone = if (phone.startsWith("+91")) phone else "+91$phone"

        lifecycleScope.launch {
            try {
                val res = ApiClient.service.signup(
                    SignupRequest(
                        name       = name,
                        phone      = formattedPhone,
                        email      = email.ifEmpty { null },
                        agency     = agency.ifEmpty { null },
                        employeeId = employeeId,
                        password   = password
                    )
                )

                if (res.isSuccessful) {
                    // Show success screen
                    tvSuccess.visibility = View.VISIBLE
                    btnSignup.visibility = View.GONE
                    tvError.visibility = View.GONE
                } else {
                    val errorBody = res.errorBody()?.string()
                    val msg = try {
                        JSONObject(errorBody ?: "").optString("error", "Signup failed. Try again.")
                    } catch (e: Exception) {
                        "Signup failed. Try again."
                    }
                    showError(msg)
                }
            } catch (e: Exception) {
                showError("Network error. Check your connection.")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnSignup.isEnabled = !loading
        btnSignup.text = if (loading) "Submitting..." else "Submit Request"
    }

    private fun showError(msg: String) {
        tvError.text = msg
        tvError.visibility = View.VISIBLE
    }

    private fun clearError() {
        tvError.visibility = View.GONE
    }
}

data class SignupRequest(
    val name: String,
    val phone: String,
    val email: String?,
    val agency: String?,
    @SerializedName("employee_id") val employeeId: String,
    val password: String
)