package com.cropsurvey.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.location.Geocoder
import com.cropsurvey.app.models.GpsCoords
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StampUtils {

    /** Best-effort reverse geocode to a short "Place, District, State" string. */
    fun reverseGeocode(ctx: Context, lat: Double, lon: Double): String = try {
        val geocoder = Geocoder(ctx, Locale.getDefault())
        @Suppress("DEPRECATION")
        val addrs = geocoder.getFromLocation(lat, lon, 1)
        if (!addrs.isNullOrEmpty()) {
            val a = addrs[0]
            val place = a.subLocality?.takeIf { it.isNotBlank() } ?: a.locality?.takeIf { it.isNotBlank() } ?: a.featureName?.takeIf { it.isNotBlank() }
            val dist  = a.subAdminArea?.takeIf { it.isNotBlank() }
            val state = a.adminArea?.takeIf { it.isNotBlank() }
            listOfNotNull(place, dist, state)
                .fold(mutableListOf<String>()) { acc, s -> if (acc.none { it.equals(s, true) }) acc.add(s); acc }
                .take(3).joinToString(", ").ifEmpty { "%.5f°N, %.5f°E".format(lat, lon) }
        } else "%.5f°N, %.5f°E".format(lat, lon)
    } catch (e: Exception) { "%.5f°N, %.5f°E".format(lat, lon) }

    /**
     * Builds a compact, semi-transparent watermark bar (place + GPS + date/time
     * + employee/case IDs) sized to the given video frame dimensions. Drawn
     * once and composited onto every frame during transcoding — the GPS and
     * timestamp reflect the moment recording started, which is sufficient for
     * authenticity verification of a short (<=30s) clip.
     */
    fun createVideoStampBitmap(
        width: Int, height: Int,
        coords: GpsCoords, placeName: String,
        empId: String, caseId: String
    ): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val unit   = width * 0.018f
        val margin = unit
        val barH   = unit * 6.2f
        val barY   = height - barH - margin
        val barRect = RectF(margin, barY, width - margin, barY + barH)
        val cornerR = unit * 0.7f

        // Semi-transparent dark background
        canvas.drawRoundRect(barRect, cornerR, cornerR,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 5, 15, 35) })

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = unit * 1.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(textPaint).apply {
            color = Color.argb(230, 200, 215, 240)
            textSize = unit * 1.3f
            typeface = Typeface.DEFAULT
        }

        val now = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        val line1 = "📍 $placeName"
        val line2 = "Lat: %.6f  Lon: %.6f  •  %s".format(coords.lat, coords.lon, now)
        val line3 = "Emp ID: $empId  •  Survey ID: $caseId"

        val padX = unit
        var ty = barY + unit * 1.9f
        canvas.drawText(line1, margin + padX, ty, textPaint)
        ty += unit * 1.9f
        canvas.drawText(line2, margin + padX, ty, subPaint)
        ty += unit * 1.7f
        canvas.drawText(line3, margin + padX, ty, subPaint)

        return bmp
    }
}
