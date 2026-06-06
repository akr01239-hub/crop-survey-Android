package com.cropsurvey.app.utils

import kotlin.math.*

/**
 * GeoBufferHelper — 50-metre polygon buffer checker
 *
 * The buffer is stored in SurveySession (in-memory, never persisted to server).
 * It is created when the polygon is confirmed, lives during draft/rejected states,
 * and is cleared when the survey is submitted or the session resets.
 *
 * All math uses the Haversine formula — no external library needed.
 */
object GeoBufferHelper {

    private const val BUFFER_METERS = 50.0
    private const val EARTH_RADIUS  = 6371000.0 // metres

    // ── Point-in-buffered-polygon check ───────────────────────────────────────

    /**
     * Returns true if [lat]/[lon] is within BUFFER_METERS of the polygon stored
     * in SurveySession.polygonGeoJson.
     *
     * Algorithm:
     *   1. Check if the point is strictly inside the polygon (ray-casting).
     *   2. If not, check if the point is within BUFFER_METERS of any polygon edge.
     */
    fun isInsideBuffer(lat: Double, lon: Double): Boolean {
        val ring = getPolygonRing() ?: return true  // no polygon → allow (failsafe)
        if (ring.size < 3) return true

        // 1. Inside the polygon itself → always OK
        if (pointInPolygon(lat, lon, ring)) return true

        // 2. Within BUFFER_METERS of any edge → OK
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[(i + 1) % ring.size]
            if (distanceToSegmentMetres(lat, lon, a.first, a.second, b.first, b.second) <= BUFFER_METERS) {
                return true
            }
        }
        return false
    }

    /**
     * Returns how many metres the point is outside the buffer, or 0 if inside.
     * Useful for the warning message.
     */
    fun distanceOutsideBuffer(lat: Double, lon: Double): Double {
        val ring = getPolygonRing() ?: return 0.0
        if (ring.size < 3) return 0.0
        if (pointInPolygon(lat, lon, ring)) return 0.0

        val minEdgeDist = ring.indices.minOf { i ->
            val a = ring[i]
            val b = ring[(i + 1) % ring.size]
            distanceToSegmentMetres(lat, lon, a.first, a.second, b.first, b.second)
        }
        return maxOf(0.0, minEdgeDist - BUFFER_METERS)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Extract ring as list of (lat, lon) pairs from SurveySession.polygonGeoJson */
    @Suppress("UNCHECKED_CAST")
    private fun getPolygonRing(): List<Pair<Double, Double>>? {
        val geo = SurveySession.polygonGeoJson ?: return null
        return try {
            val geometry = when (geo["type"]) {
                "Feature"           -> geo["geometry"] as? Map<String, Any?> ?: return null
                "FeatureCollection" -> {
                    val feats = geo["features"] as? List<*> ?: return null
                    (feats.firstOrNull() as? Map<String, Any?>)?.get("geometry") as? Map<String, Any?> ?: return null
                }
                else                -> geo
            }
            val coords = (geometry["coordinates"] as? List<*>)?.firstOrNull() as? List<*> ?: return null
            coords.mapNotNull { pt ->
                val pair = pt as? List<*> ?: return@mapNotNull null
                val lng  = (pair[0] as? Number)?.toDouble() ?: return@mapNotNull null
                val lat  = (pair[1] as? Number)?.toDouble() ?: return@mapNotNull null
                Pair(lat, lng)
            }
        } catch (e: Exception) { null }
    }

    /** Ray-casting point-in-polygon */
    private fun pointInPolygon(lat: Double, lon: Double, ring: List<Pair<Double, Double>>): Boolean {
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val xi = ring[i].second; val yi = ring[i].first
            val xj = ring[j].second; val yj = ring[j].first
            if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /** Haversine distance between two lat/lon points in metres */
    private fun haversineMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return EARTH_RADIUS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Minimum distance from point P to line segment AB, in metres */
    private fun distanceToSegmentMetres(
        pLat: Double, pLon: Double,
        aLat: Double, aLon: Double,
        bLat: Double, bLon: Double
    ): Double {
        val abDist = haversineMetres(aLat, aLon, bLat, bLon)
        if (abDist < 0.001) return haversineMetres(pLat, pLon, aLat, aLon) // degenerate segment

        // Project P onto AB using flat approximation (valid for small distances)
        val ax = aLon; val ay = aLat
        val bx = bLon; val by = bLat
        val px = pLon; val py = pLat

        val abx = bx - ax; val aby = by - ay
        val t = ((px - ax) * abx + (py - ay) * aby) / (abx * abx + aby * aby)
        val tc = t.coerceIn(0.0, 1.0)

        val closestLat = ay + tc * aby
        val closestLon = ax + tc * abx
        return haversineMetres(pLat, pLon, closestLat, closestLon)
    }
}
