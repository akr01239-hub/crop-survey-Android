package com.cropsurvey.app.network

import com.cropsurvey.app.auth.*
import com.cropsurvey.app.config.AppConfig
import com.cropsurvey.app.models.*
import okhttp3.MultipartBody
import org.json.JSONObject
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface ApiService {

    // ─── Auth ─────────────────────────────────────────────────────────────────

    @POST("auth/login")
    suspend fun loginWithPassword(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/signup")
    suspend fun signup(@Body request: SignupRequest): Response<Map<String, Any>>

    @POST("auth/otp/send")
    suspend fun sendOtp(@Body request: OtpSendRequest): Response<Map<String, Any>>

    @POST("auth/otp/verify")
    suspend fun verifyOtp(@Body request: OtpVerifyRequest): Response<AuthResponse>

    @GET("auth/me")
    suspend fun getMe(): Response<User>

    @GET("auth/check-phone")
    suspend fun checkPhoneExists(@Query("phone") phone: String): Response<Map<String, Any>>

    @POST("auth/verify-pan")
    suspend fun verifyPanForReset(@Body request: VerifyPanRequest): Response<Map<String, Any>>

    @POST("auth/reset-password-pan")
    suspend fun resetPasswordByPan(@Body request: ResetPasswordPanRequest): Response<Map<String, Any>>

    @POST("auth/login-log")
    suspend fun sendLoginLog(@Body request: LoginLogRequest): Response<Map<String, Any>>

    @POST("surveys/farmer-otp/send")
    suspend fun sendFarmerOtp(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("surveys/farmer-otp/verify")
    suspend fun verifyFarmerOtp(@Body body: Map<String, String>): Response<Map<String, Any>>

    // ─── Locations ────────────────────────────────────────────────────────────

    @GET("locations/states")
    suspend fun getStates(): Response<List<String>>

    @GET("locations/districts")
    suspend fun getDistricts(@Query("state") state: String): Response<List<String>>

    @GET("locations/sub-districts")
    suspend fun getSubDistricts(
        @Query("state") state: String,
        @Query("district") district: String
    ): Response<List<String>>

    // ─── Surveys ──────────────────────────────────────────────────────────────

    @POST("surveys")
    suspend fun createSurvey(@Body request: CreateSurveyRequest): Response<Survey>

    @PUT("surveys/{id}")
    suspend fun updateSurvey(
        @Path("id") id: String,
        @Body request: UpdateSurveyRequest
    ): Response<Survey>

    @POST("surveys/{id}/submit")
    suspend fun submitSurvey(@Path("id") id: String): Response<Survey>

    @GET("surveys/{id}")
    suspend fun getSurvey(@Path("id") id: String): Response<Survey>

    @GET("surveys")
    suspend fun getSurveys(
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 25,
        @Query("mine") mine: Boolean = true   // Always scope to the logged-in user in the mobile app
    ): Response<SurveyListResponse>

    @DELETE("surveys/{id}")
    suspend fun deleteSurvey(@Path("id") id: String): Response<ResponseBody>

    // ─── Photos ───────────────────────────────────────────────────────────────

    @Multipart
    @POST("surveys/{id}/photos")
    suspend fun uploadPhoto(
        @Path("id") surveyId: String,
        @Part photo: MultipartBody.Part,
        @Part("photo_key") photoKey: okhttp3.RequestBody,
        @Part("label") label: okhttp3.RequestBody,
        @Part("lat") lat: okhttp3.RequestBody,
        @Part("lon") lon: okhttp3.RequestBody,
        @Part("accuracy") accuracy: okhttp3.RequestBody,
        @Part("captured_at") capturedAt: okhttp3.RequestBody
    ): Response<SurveyPhoto>

    @GET("surveys/{id}/photos")
    suspend fun getPhotos(@Path("id") surveyId: String): Response<List<SurveyPhoto>>
}

// ─── API Client Singleton ──────────────────────────────────────────────────────

// Global callback — set by App to show crash dialog from any screen
typealias SecurityViolationHandler = (code: String, message: String) -> Unit

object ApiClient {

    private var accessToken: String? = null
    var deviceId: String? = null
    var onSecurityViolation: SecurityViolationHandler? = null

    fun setToken(token: String?) { accessToken = token }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)  // generous — photo/video uploads on slow rural networks
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()
            // Only set JSON content-type for requests that don't already specify one
            // (multipart/form-data uploads set their own boundary-bearing content-type
            // on the request body — forcing application/json here broke uploads).
            if (original.header("Content-Type") == null && original.body?.contentType() == null) {
                builder.header("Content-Type", "application/json")
            }
            accessToken?.let { builder.header("Authorization", "Bearer $it") }
            deviceId?.let { builder.header("X-Device-Id", it) }
            val response = chain.proceed(builder.build())
            // Intercept 403 security errors globally — show crash dialog from any screen
            if (response.code == 403) {
                try {
                    val body = response.peekBody(4096).string()
                    val json = org.json.JSONObject(body)
                    val code = json.optString("code", "")
                    val msg  = json.optString("error", "")
                    if (code in listOf("DEVICE_LOCKED", "ACCOUNT_BLOCKED", "MOCK_LOCATION")) {
                        onSecurityViolation?.invoke(code, msg)
                    }
                } catch (_: Exception) {}
            }
            response
        }
        .build()

    val service: ApiService = Retrofit.Builder()
        .baseUrl("${AppConfig.API_BASE_URL}/")
        .client(httpClient)
        .addConverterFactory(
            // Default Gson turns ALL numbers in Map<String,Any?> into Double (e.g. 2022 → 2022.0).
            // LONG_OR_DOUBLE keeps integers as Long and only uses Double for decimals,
            // which means form_data values round-trip correctly when restored from draft.
            GsonConverterFactory.create(
                GsonBuilder()
                    .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
                    .create()
            )
        )
        .build()
        .create(ApiService::class.java)
}