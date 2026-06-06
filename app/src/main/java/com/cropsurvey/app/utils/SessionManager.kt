package com.cropsurvey.app.utils

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.cropsurvey.app.models.User
import com.cropsurvey.app.network.ApiClient
import com.google.gson.Gson

object SessionManager {

    private const val PREF_NAME        = "crop_survey_session"
    private const val KEY_ACCESS_TOKEN  = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_USER          = "user"
    private const val KEY_EMPLOYEE_ID   = "employee_id"
    private const val KEY_DEVICE_ID     = "registered_device_id"   // NEW: locked device

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        getAccessToken()?.let { ApiClient.setToken(it) }
    }

    fun saveSession(accessToken: String, refreshToken: String, user: User, deviceId: String? = null) {
        val editor = prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_USER, gson.toJson(user))

        user.employeeId?.ifBlank { null }?.let { editor.putString(KEY_EMPLOYEE_ID, it) }

        // Save the device ID that performed this login — locked for future sessions
        deviceId?.let { editor.putString(KEY_DEVICE_ID, it) }

        editor.apply()
        ApiClient.setToken(accessToken)
    }

    fun getRegisteredDeviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)

    fun cacheEmployeeId(empId: String) {
        prefs.edit().putString(KEY_EMPLOYEE_ID, empId).apply()
    }

    fun getCachedEmployeeId(): String? = prefs.getString(KEY_EMPLOYEE_ID, null)

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun getUser(): User? {
        val json = prefs.getString(KEY_USER, null) ?: return null
        return try { gson.fromJson(json, User::class.java) } catch (e: Exception) { null }
    }

    fun isLoggedIn(): Boolean = getAccessToken() != null

    fun clearSession() {
        prefs.edit().clear().apply()
        ApiClient.setToken(null)
    }

    /** Returns true if this device is the one that originally logged into the stored session */
    fun isCurrentDevice(context: Context): Boolean {
        val registeredId = getRegisteredDeviceId() ?: return true  // no record = first login
        val currentId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return registeredId == currentId
    }
}