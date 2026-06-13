package com.cropsurvey.app.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.cropsurvey.app.models.GpsCoords
import com.cropsurvey.app.utils.SurveySession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * GeoTagOverlayComponent - single source of truth for the geo-tag stamp
 * rendered on captured media (the "card" showing address, lat/lon, accuracy,
 * altitude, date/time, employee ID, survey ID, crop, etc).
 *
 * Used by:
 *  - PhotoCaptureActivity (burned into the saved JPEG, and as the live preview overlay)
 *  - VideoStampTranscoder (burned into every frame of recorded video)
 *  - Any future media type
 *
 * Any visual change here automatically applies everywhere it's used - there
 * is no separate per-media-type stamp implementation.
 */
object GeoTagOverlay {

    data class Cell(
        val label: String,
        val value: String,
        val iconBg: Int,
        val iconFg: Int
    )

    /**
     * Draws the full geo-tag stamp card onto [canvas], anchored to the
     * bottom-left of a [W]x[H] surface (matches the photo watermark layout
     * exactly). [survId] is the internal survey id (used as a fallback if
     * SurveySession.currentCaseId is empty).
     */
    fun drawStamp(canvas: Canvas, W: Float, H: Float, coords: GpsCoords, placeName: String, survId: String) {
        val margin  = W * 0.012f
        val cardW   = W - margin * 2f
        val unit    = W * 0.008f
        val headerH = unit * 11f
        val rowH    = unit * 9f
        val cardH   = headerH + 3 * rowH
        val cardX   = margin
        val cardY   = H - cardH - margin
        val cornerR = unit * 1.8f
        val thirdW  = cardW / 3f

        canvas.drawRoundRect(RectF(cardX+unit*0.6f, cardY+unit*0.6f, cardX+cardW+unit*0.6f, cardY+cardH+unit*0.6f), cornerR, cornerR,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70,0,0,0) })

        val cardRect = RectF(cardX, cardY, cardX+cardW, cardY+cardH)

        canvas.drawRoundRect(cardRect, cornerR, cornerR,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(252,255,255,255) })

        val clipPath = Path().apply { addRoundRect(cardRect, cornerR, cornerR, Path.Direction.CW) }
        canvas.save(); canvas.clipPath(clipPath)
        canvas.drawRect(RectF(cardX, cardY, cardX+cardW, cardY+headerH),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.LinearGradient(cardX, cardY, cardX+cardW, cardY,
                    Color.parseColor("#02122F"), Color.parseColor("#071B46"), android.graphics.Shader.TileMode.CLAMP)
            })
        canvas.drawRect(RectF(cardX, cardY+headerH, cardX+cardW, cardY+cardH),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(252,255,255,255) })
        canvas.restore()

        canvas.drawRoundRect(cardRect, cornerR, cornerR,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160,100,130,200); style = Paint.Style.STROKE; strokeWidth = unit*0.5f })

        val divP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(50,180,190,210); strokeWidth = unit*0.25f }
        for (r in 1..3) canvas.drawLine(cardX, cardY+headerH+r*rowH, cardX+cardW, cardY+headerH+r*rowH, divP)
        canvas.drawLine(cardX+thirdW,   cardY+headerH, cardX+thirdW,   cardY+cardH, divP)
        canvas.drawLine(cardX+thirdW*2, cardY+headerH, cardX+thirdW*2, cardY+cardH, divP)

        val pinR = unit*1.8f; val pinCX = cardX+unit*2.5f+pinR; val pinCY = cardY+headerH*0.5f
        canvas.drawCircle(pinCX, pinCY, pinR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EF4444") })
        canvas.drawCircle(pinCX, pinCY, pinR*0.36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        val hPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = unit*3.0f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val placeX = pinCX+pinR+unit*1.2f
        canvas.drawText(clip(placeName, hPaint, cardX+cardW-placeX-unit*1.5f), placeX, pinCY+unit*0.9f, hPaint)

        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val tdf = SimpleDateFormat("hh:mm:ss aa", Locale.getDefault())
        val now = Date()
        val uId = SurveySession.employeeId.ifEmpty { SurveySession.userId.take(8).uppercase().ifEmpty { "-" } }
        val caseId = SurveySession.currentCaseId
        val sId = if (caseId.isNotEmpty()) caseId else if (survId.isNotEmpty()) survId.take(8).uppercase() else "-"
        val alt = coords.altitude?.let { "${"%.1f".format(it)}m" } ?: "0m"
        val acc = "+/-${coords.accuracy?.toInt() ?: 0}m"

        val blue   = Pair(Color.parseColor("#DBEAFE"), Color.parseColor("#1D4ED8"))
        val green  = Pair(Color.parseColor("#D1FAE5"), Color.parseColor("#047857"))
        val purple = Pair(Color.parseColor("#EDE9FE"), Color.parseColor("#6D28D9"))
        val amber  = Pair(Color.parseColor("#FEF3C7"), Color.parseColor("#B45309"))
        val cyan   = Pair(Color.parseColor("#CFFAFE"), Color.parseColor("#0E7490"))
        val teal   = Pair(Color.parseColor("#CCFBF1"), Color.parseColor("#0F766E"))
        val green2 = Pair(Color.parseColor("#DCFCE7"), Color.parseColor("#15803D"))

        val cropName = (SurveySession.formData["crop_name"] as? String)
            ?: (SurveySession.formData["crop"] as? String)
            ?: "-"

        val grid = listOf(
            listOf(Cell("Latitude",  "%.6f".format(coords.lat), blue.first,   blue.second),
                Cell("Longitude", "%.6f".format(coords.lon), green.first,  green.second),
                Cell("Accuracy",  acc,                        purple.first, purple.second)),
            listOf(Cell("Altitude",  alt,                        amber.first,  amber.second),
                Cell("Date",      sdf.format(now),            blue.first,   blue.second),
                Cell("Time",      tdf.format(now),            purple.first, purple.second)),
            listOf(Cell("Emp ID",    uId,                        cyan.first,   cyan.second),
                Cell("Survey ID", sId,                        teal.first,   teal.second),
                Cell("Crop",      cropName,                   green2.first, green2.second))
        )

        val labelP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1D2B4D"); textSize = unit*2.1f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL) }
        val colonP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#6B7280"); textSize = unit*2.1f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL) }
        val valueP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#000000"); textSize = unit*2.4f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val iconR2 = unit*1.6f; val cellPad = unit*1.8f; val gap = unit*0.8f

        fun baselineY(midY: Float, p: Paint): Float { val fm = p.fontMetrics; return midY-(fm.ascent+fm.descent)/2f }

        grid.forEachIndexed { rowIdx, cells ->
            val midY = cardY + headerH + rowIdx * rowH + rowH * 0.5f
            cells.forEachIndexed { colIdx, cell ->
                if (cell.label.isEmpty()) return@forEachIndexed
                val cellLeft = cardX + colIdx * thirdW
                drawCell(canvas, cell, cellLeft, cellLeft+thirdW, cellPad, midY, iconR2, gap, labelP, colonP, valueP) { p: Paint -> baselineY(midY, p) }
            }
        }
    }

    /**
     * Renders just the stamp card as its own bitmap (transparent background),
     * sized to [width]. Used for the live camera preview overlay.
     */
    fun renderStampBitmap(width: Int, coords: GpsCoords, placeName: String, survId: String): Bitmap {
        val W = width.toFloat()
        val unit    = W * 0.008f
        val headerH = unit * 11f
        val rowH    = unit * 9f
        val cardH   = headerH + 3 * rowH
        val height  = (cardH + unit * 3).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawStamp(Canvas(bmp), W, height.toFloat(), coords, placeName, survId)
        return bmp
    }

    /**
     * Renders the stamp card sized for a full video frame ([width]x[height]),
     * anchored bottom-left exactly like the photo watermark.
     */
    fun renderStampForFrame(width: Int, height: Int, coords: GpsCoords, placeName: String, survId: String): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawStamp(Canvas(bmp), width.toFloat(), height.toFloat(), coords, placeName, survId)
        return bmp
    }

    private fun drawCell(
        canvas: Canvas,
        cell: Cell,
        cellLeft: Float,
        cellRight: Float,
        cellPad: Float,
        midY: Float,
        iconR: Float,
        gap: Float,
        labelP: Paint,
        colonP: Paint,
        valueP: Paint,
        baselineY: (Paint) -> Float
    ) {
        val iconCX = cellLeft + cellPad + iconR
        canvas.drawCircle(iconCX, midY, iconR,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cell.iconBg })
        canvas.drawCircle(iconCX, midY, iconR * 0.38f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cell.iconFg })

        val textStart  = iconCX + iconR + gap
        val totalAvail = cellRight - textStart - cellPad * 0.5f

        val colonW = colonP.measureText(" : ")

        val labelMaxW = totalAvail * 0.38f
        val labelStr  = clip(cell.label, labelP, labelMaxW)
        val labelW    = labelP.measureText(labelStr)

        val valueMaxW = totalAvail - labelW - colonW - gap * 0.5f
        val valueStr  = clip(cell.value, valueP, valueMaxW)

        var x = textStart
        canvas.drawText(labelStr, x, baselineY(labelP), labelP)
        x += labelW
        canvas.drawText(" : ", x, baselineY(colonP), colonP)
        x += colonW
        canvas.drawText(valueStr, x, baselineY(valueP), valueP)
    }

    /** Best-effort reverse geocode to a short "Place, District, State" string — shared address service. */
    fun reverseGeocode(ctx: android.content.Context, lat: Double, lon: Double): String = try {
        val geocoder = android.location.Geocoder(ctx, Locale.getDefault())
        @Suppress("DEPRECATION")
        val addrs = geocoder.getFromLocation(lat, lon, 1)
        if (!addrs.isNullOrEmpty()) {
            val a = addrs[0]
            val place = a.subLocality?.takeIf { it.isNotBlank() } ?: a.locality?.takeIf { it.isNotBlank() } ?: a.featureName?.takeIf { it.isNotBlank() }
            val dist  = a.subAdminArea?.takeIf { it.isNotBlank() }
            val state = a.adminArea?.takeIf { it.isNotBlank() }
            listOfNotNull(place, dist, state)
                .fold(mutableListOf<String>()) { acc, s -> if (acc.none { it.equals(s, true) }) acc.add(s); acc }
                .take(3).joinToString(", ").ifEmpty { "%.5f, %.5f".format(lat, lon) }
        } else "%.5f, %.5f".format(lat, lon)
    } catch (e: Exception) { "%.5f, %.5f".format(lat, lon) }

    /** Truncate [text] with "..." to fit within [maxW] pixels. */
    fun clip(text: String, paint: Paint, maxW: Float): String {
        if (paint.measureText(text) <= maxW) return text
        var t = text
        while (t.isNotEmpty() && paint.measureText("$t...") > maxW) t = t.dropLast(1)
        return "$t..."
    }
}
