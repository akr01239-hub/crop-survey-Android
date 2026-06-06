package com.cropsurvey.app.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.cropsurvey.app.config.AppConfig
import com.cropsurvey.app.models.GpsCoords
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.system.exitProcess

// ─── GPS Helper ───────────────────────────────────────────────────────────────

object GpsHelper {

    suspend fun getCurrentLocation(context: Context): GpsCoords? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return null

        return try {
            withContext(Dispatchers.IO) {
                withTimeout(AppConfig.GPS_TIMEOUT_SECONDS * 1000L) {
                    suspendCancellableCoroutine { cont ->
                        val fusedClient = LocationServices.getFusedLocationProviderClient(context)

                        val request = LocationRequest.Builder(
                            Priority.PRIORITY_HIGH_ACCURACY,
                            1000L
                        ).setMaxUpdates(1).build()

                        val callback = object : LocationCallback() {
                            override fun onLocationResult(result: LocationResult) {
                                val loc = result.lastLocation
                                fusedClient.removeLocationUpdates(this)
                                if (loc != null) {
                                    val isMocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                        loc.isMock
                                    else
                                        @Suppress("DEPRECATION") loc.isFromMockProvider

                                    cont.resume(
                                        GpsCoords(
                                            lat      = loc.latitude,
                                            lon      = loc.longitude,
                                            accuracy = loc.accuracy,
                                            altitude = if (loc.hasAltitude()) loc.altitude else null,
                                            isMocked = isMocked
                                        )
                                    )
                                } else {
                                    cont.resume(null)
                                }
                            }
                        }

                        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
                        cont.invokeOnCancellation { fusedClient.removeLocationUpdates(callback) }
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun formatCoords(coords: GpsCoords): String {
        return "%.6f° N  %.6f° E  ±%.0fm".format(coords.lat, coords.lon, coords.accuracy ?: 0f)
    }

    fun getAccuracyColor(accuracy: Float?): Int {
        return when {
            accuracy == null -> android.graphics.Color.WHITE
            accuracy < 10f   -> android.graphics.Color.parseColor(AppConfig.Colors.SUCCESS)
            accuracy < 30f   -> android.graphics.Color.parseColor(AppConfig.Colors.WARNING)
            else             -> android.graphics.Color.parseColor(AppConfig.Colors.DANGER)
        }
    }
}

// ─── Mock Location Detector ───────────────────────────────────────────────────

object MockLocationDetector {

    /**
     * Checks if mock/fake location is active using multiple detection methods.
     * Returns true if any signal of mock location is found.
     */
    fun isMockLocation(context: Context): Boolean {
        if (isMockLocationEnabled(context)) return true
        if (isEmulator()) return true
        if (isMockAppInstalled(context)) return true
        return false
    }

    /**
     * Shows a NON-DISMISSABLE alert dialog and then force-kills the app.
     * Call this from the UI thread when mock location is detected.
     */
    fun showCrashDialogAndKill(activity: Activity) {
        val dialog = AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("⚠️ Fake Location Detected")
            .setMessage(
                "यह ऐप नकली/Mock Location के साथ काम नहीं करता।\n\n" +
                        "This app has detected that you are using a Mock Location or GPS spoofer.\n\n" +
                        "🌾 Crop survey data must reflect real field locations to protect farmers " +
                        "and ensure accurate agricultural records.\n\n" +
                        "Using fake GPS leads to incorrect crop insurance, subsidy disbursements, " +
                        "and government benefits for farmers — which is fraud.\n\n" +
                        "Please disable Mock Location from Developer Options and reopen the app.\n\n" +
                        "If you believe this is an error, contact Admin: Akshay Rai."
            )
            .setCancelable(false)
            .setPositiveButton("Close App") { _, _ -> forceKill(activity) }
            .create()

        dialog.setCanceledOnTouchOutside(false)
        dialog.show()

        // Also schedule a kill after 30 seconds in case they don't tap the button
        android.os.Handler(Looper.getMainLooper()).postDelayed({
            if (!activity.isFinishing) forceKill(activity)
        }, 30_000)
    }

    private fun forceKill(activity: Activity) {
        activity.finishAffinity()
        android.os.Process.killProcess(android.os.Process.myPid())
        exitProcess(1)
    }

    private fun isMockLocationEnabled(context: Context): Boolean {
        return try {
            val mockLocation = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ALLOW_MOCK_LOCATION
            )
            mockLocation == "1"
        } catch (e: Exception) {
            false
        }
    }

    /** Checks for known mock GPS apps installed on the device */
    private fun isMockAppInstalled(context: Context): Boolean {
        val mockApps = listOf(
            "com.lexa.fakegps",
            "com.incorporateapps.fakegps.fre",
            "com.blogspot.newapphorizons.fakegps",
            "com.lkr.fakelocation",
            "com.rosteam.gpsemulator",
            "com.theappninjas.gpsjoystick",
            "com.gsmartstudio.fakegps",
            "com.byterev.teleporter",
            "com.fly.gps",
            "com.evezzon.fakegps",
            "com.kds.gps.speed.tracker.lite",
            "fake.gps.location",
            "gps.mock.provider"
        )
        val pm = context.packageManager
        return mockApps.any { pkg ->
            try { pm.getPackageInfo(pkg, 0); true } catch (e: Exception) { false }
        }
    }

    private fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                || "google_sdk" == Build.PRODUCT)
    }

    fun isLocationMocked(location: Location): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }
    }
}