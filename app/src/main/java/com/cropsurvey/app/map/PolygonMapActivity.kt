package com.cropsurvey.app.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.cropsurvey.app.BaseActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cropsurvey.app.R
import com.cropsurvey.app.guide.AiGuideOverlay
import com.cropsurvey.app.config.AppConfig
import com.cropsurvey.app.models.CreateSurveyRequest
import com.cropsurvey.app.network.ApiClient
import com.cropsurvey.app.survey.SurveyTabsActivity
import com.cropsurvey.app.utils.MockLocationDetector
import com.cropsurvey.app.utils.SurveySession
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.launch
import kotlin.math.*

class PolygonMapActivity : BaseActivity(), OnMapReadyCallback {

    private lateinit var tvPointCount: TextView
    private lateinit var tvArea: TextView
    private lateinit var tvSurveyType: TextView
    private lateinit var btnUndo: Button
    private lateinit var btnClear: Button
    private lateinit var btnConfirm: Button
    private lateinit var tvHint: TextView
    private lateinit var progressBar: ProgressBar

    private var googleMap: GoogleMap? = null
    private val vertices = mutableListOf<LatLng>()
    private var polygon: com.google.android.gms.maps.model.Polygon? = null
    private val markers = mutableListOf<Marker>()
    private var bufferCircle: Circle? = null
    private var youAreHereMarker: Marker? = null
    private var currentLatLng: LatLng? = null
    private var mapReady = false
    // The zoom level that fits exactly the 200 m buffer — calculated once per location fix.
    // The user may zoom IN beyond this but never OUT beyond it.
    private var bufferZoom: Float = 15f
    // True while we are programmatically moving the camera, so the idle listener
    // doesn't trigger a second recenter on top of our own animation.
    private var isProgrammaticMove = false

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    private lateinit var surveyType: String
    private var farmerVerified: Boolean = false
    private var farmerPhone: String = ""

    companion object {
        private const val BUFFER_RADIUS_METERS = 200.0
    }

    // ── Runtime permission launcher ──────────────────────────────────────────
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
                || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            startLocationUpdates()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Location Required")
                .setMessage("This screen needs your GPS location to work. Please grant location permission.")
                .setPositiveButton("OK") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_polygon_map)
        AiGuideOverlay.show(this, AiGuideOverlay.Step.MAP_DRAW)

        surveyType      = intent.getStringExtra("survey_type") ?: "CLS"
        farmerVerified  = intent.getBooleanExtra("farmer_verified", false)
        farmerPhone     = intent.getStringExtra("farmer_phone") ?: ""

        bindViews()
        setupButtons()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment: SupportMapFragment
        if (savedInstanceState == null) {
            mapFragment = SupportMapFragment.newInstance()
            supportFragmentManager.beginTransaction()
                .replace(R.id.map_fragment, mapFragment)
                .commit()
        } else {
            mapFragment = supportFragmentManager
                .findFragmentById(R.id.map_fragment) as SupportMapFragment
        }
        mapFragment.getMapAsync(this)

        if (MockLocationDetector.isMockLocation(this)) {
            AlertDialog.Builder(this)
                .setTitle("Security Violation")
                .setMessage("Mock location detected. Real GPS is required for surveys.")
                .setPositiveButton("OK") { _, _ -> finish() }
                .setCancelable(false)
                .show()
            return
        }

        tvSurveyType.text = surveyType

        // Request permission here
        checkAndRequestLocationPermission()
    }

    private fun checkAndRequestLocationPermission() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            // Already granted — start updates (map might not be ready yet, handled in onMapReady)
            if (mapReady) startLocationUpdates()
        } else {
            // Ask the user
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun bindViews() {
        tvPointCount = findViewById(R.id.tv_point_count)
        tvArea       = findViewById(R.id.tv_area)
        tvSurveyType = findViewById(R.id.tv_survey_type)
        btnUndo      = findViewById(R.id.btn_undo)
        btnClear     = findViewById(R.id.btn_clear)
        btnConfirm   = findViewById(R.id.btn_confirm)
        tvHint       = findViewById(R.id.tv_hint)
        progressBar  = findViewById(R.id.progress_bar)
    }

    private fun setupButtons() {
        btnUndo.setOnClickListener {
            if (vertices.isNotEmpty()) {
                vertices.removeAt(vertices.size - 1)
                markers.lastOrNull()?.remove()
                markers.removeLastOrNull()
                updatePolygon()
            }
        }

        btnClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear Boundary")
                .setMessage("Remove all points and start over?")
                .setPositiveButton("Clear") { _, _ ->
                    vertices.clear()
                    markers.forEach { it.remove() }
                    markers.clear()
                    polygon?.remove()
                    polygon = null
                    updateUI()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnConfirm.setOnClickListener {
            if (vertices.size >= 3) createSurveyAndProceed()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        mapReady = true
        googleMap = map
        map.mapType = GoogleMap.MAP_TYPE_HYBRID
        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isMyLocationButtonEnabled = false
        map.uiSettings.isScrollGesturesEnabled = true   // pan allowed — idle listener snaps back
        map.uiSettings.isZoomGesturesEnabled = true

        // Native my-location layer disabled — we use custom "You are here" marker instead
        // Max zoom only; the min-zoom floor is enforced dynamically in the idle listener
        // so the user can zoom IN freely but cannot zoom OUT past the 200 m buffer level.
        map.setMaxZoomPreference(21f)

        map.setOnCameraIdleListener {
            val loc = currentLatLng ?: return@setOnCameraIdleListener
            if (isProgrammaticMove) return@setOnCameraIdleListener

            val cam = map.cameraPosition
            val distanceMoved = distanceMeters(cam.target, loc)
            val zoomedOutTooFar = cam.zoom < bufferZoom - 0.05f   // small epsilon avoids float jitter

            // Snap back if: panned away from location OR zoomed out past the 200 m buffer floor
            if (distanceMoved > 20 || zoomedOutTooFar) {
                val targetZoom = if (zoomedOutTooFar) bufferZoom else cam.zoom
                snapCameraTo(loc, targetZoom)
            }
        }

        map.setOnMapClickListener { latLng ->
            vertices.add(latLng)
            val marker = map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .icon(smallDotIcon(Color.parseColor("#EF4444")))
                    .anchor(0.5f, 0.5f)
                    .draggable(true)
            )
            marker?.let { markers.add(it) }
            updatePolygon()
        }

        map.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
            override fun onMarkerDragStart(marker: Marker) {}
            override fun onMarkerDrag(marker: Marker) {}
            override fun onMarkerDragEnd(marker: Marker) {
                val idx = markers.indexOf(marker)
                if (idx >= 0) {
                    vertices[idx] = marker.position
                    updatePolygon()
                }
            }
        })

        // If user taps the "You are here" marker, treat it as a map tap (add vertex)
        map.setOnMarkerClickListener { marker ->
            if (marker == youAreHereMarker) {
                // pass tap through as a map click
                vertices.add(marker.position)
                val m = map.addMarker(
                    MarkerOptions()
                        .position(marker.position)
                        .icon(smallDotIcon(Color.parseColor("#EF4444")))
                        .anchor(0.5f, 0.5f)
                        .draggable(true)
                )
                m?.let { markers.add(it) }
                updatePolygon()
                true
            } else {
                false
            }
        }

        // If permission was already granted before map was ready, start now
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) return
        if (!mapReady) return

        // Stop any existing updates first
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val latLng = LatLng(loc.latitude, loc.longitude)
                currentLatLng = latLng
                centerMapOnLocation(latLng)
                updateBufferCircle(latLng)
            }
        }

        fusedLocationClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())

        // Also get last known location for instant display while waiting for fresh fix
        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null && currentLatLng == null) {
                val latLng = LatLng(loc.latitude, loc.longitude)
                currentLatLng = latLng
                centerMapOnLocation(latLng)
                updateBufferCircle(latLng)
            }
        }
    }

    /** Haversine distance in metres between two LatLng points. */
    private fun distanceMeters(a: LatLng, b: LatLng): Double {
        val R = 6371000.0
        val lat1 = Math.toRadians(a.latitude); val lat2 = Math.toRadians(b.latitude)
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * R * asin(sqrt(h))
    }

    private fun zoomForRadius(radiusMeters: Double, latitude: Double): Float {
        val screenWidthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        val targetPixels = screenWidthDp * resources.displayMetrics.density / 2
        val metersPerPixel = radiusMeters / targetPixels
        val zoom = ln(156543.0 * cos(Math.toRadians(latitude)) / metersPerPixel) / ln(2.0)
        return zoom.toFloat().coerceIn(14f, 20f)
    }

    /** Called on every GPS update — re-centers map and refreshes the buffer zoom floor. */
    private fun centerMapOnLocation(latLng: LatLng) {
        val map = googleMap ?: return
        bufferZoom = zoomForRadius(BUFFER_RADIUS_METERS, latLng.latitude)

        // Only move the camera if the current view is already at (or below) the buffer floor,
        // so we don't interrupt a deliberate zoom-in the surveyor has made.
        val currentZoom = map.cameraPosition.zoom
        val targetZoom = if (currentZoom < bufferZoom) bufferZoom else currentZoom
        snapCameraTo(latLng, targetZoom)
    }

    /** Programmatically move the camera without triggering snap-back in the idle listener. */
    private fun snapCameraTo(latLng: LatLng, zoom: Float) {
        val map = googleMap ?: return
        isProgrammaticMove = true
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(latLng, zoom),
            300,
            object : GoogleMap.CancelableCallback {
                override fun onFinish() { isProgrammaticMove = false }
                override fun onCancel() { isProgrammaticMove = false }
            }
        )
    }

    private fun updateBufferCircle(center: LatLng) {
        val map = googleMap ?: return
        bufferCircle?.remove()
        bufferCircle = map.addCircle(
            CircleOptions()
                .center(center)
                .radius(BUFFER_RADIUS_METERS)
                .strokeColor(Color.parseColor("#2563EB"))
                .strokeWidth(3f)
                .fillColor(Color.argb(30, 37, 99, 235))
        )

        // "You are here" marker with label
        youAreHereMarker?.remove()
        youAreHereMarker = map.addMarker(
            MarkerOptions()
                .position(center)
                .icon(youAreHereIcon())
                .anchor(0.5f, 1.0f)
                .zIndex(-1f)
        )
    }

    private fun youAreHereIcon(): BitmapDescriptor {
        val density = resources.displayMetrics.density
        val dotRadius = 10f * density
        val labelTextSize = 11f * density
        val padding = 8f * density
        val tailHeight = 10f * density

        // Measure label text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = labelTextSize
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val label = "You are here"
        val textWidth = textPaint.measureText(label)
        val textBounds = android.graphics.Rect()
        textPaint.getTextBounds(label, 0, label.length, textBounds)
        val textHeight = textBounds.height().toFloat()

        val bubbleWidth = textWidth + padding * 2
        val bubbleHeight = textHeight + padding * 2
        val totalWidth = maxOf(bubbleWidth, dotRadius * 2) + 4 * density
        val totalHeight = bubbleHeight + tailHeight + dotRadius * 2 + 4 * density

        val bmp = Bitmap.createBitmap(totalWidth.toInt(), totalHeight.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val cx = totalWidth / 2f

        // Draw blue dot at bottom
        val dotCy = totalHeight - dotRadius - 2 * density
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2563EB") }
        val dotStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
        }
        canvas.drawCircle(cx, dotCy, dotRadius, dotPaint)
        canvas.drawCircle(cx, dotCy, dotRadius, dotStroke)

        // Draw bubble above dot
        val bubbleLeft = cx - bubbleWidth / 2
        val bubbleTop = 2 * density
        val bubbleRight = cx + bubbleWidth / 2
        val bubbleBottom = bubbleTop + bubbleHeight
        val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2563EB") }
        val cornerRadius = 6f * density
        canvas.drawRoundRect(bubbleLeft, bubbleTop, bubbleRight, bubbleBottom, cornerRadius, cornerRadius, bubblePaint)

        // Draw tail triangle
        val tailPath = android.graphics.Path().apply {
            moveTo(cx - 6 * density, bubbleBottom)
            lineTo(cx + 6 * density, bubbleBottom)
            lineTo(cx, bubbleBottom + tailHeight)
            close()
        }
        canvas.drawPath(tailPath, bubblePaint)

        // Draw label text
        val textX = cx - textWidth / 2
        val textY = bubbleTop + padding + textHeight
        canvas.drawText(label, textX, textY, textPaint)

        return BitmapDescriptorFactory.fromBitmap(bmp)
    }

    private fun smallDotIcon(color: Int, radiusDp: Float = 6f): BitmapDescriptor {
        val density = resources.displayMetrics.density
        val radius = radiusDp * density
        val size = (radius * 2 + 4 * density).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2 * density
        }
        val cx = size / 2f; val cy = size / 2f
        canvas.drawCircle(cx, cy, radius, fill)
        canvas.drawCircle(cx, cy, radius, stroke)
        return BitmapDescriptorFactory.fromBitmap(bmp)
    }

    private fun updatePolygon() {
        polygon?.remove()
        if (vertices.size >= 3) {
            polygon = googleMap?.addPolygon(
                PolygonOptions()
                    .addAll(vertices)
                    .strokeColor(Color.parseColor("#E53935"))
                    .strokeWidth(3f)
                    .fillColor(Color.argb(40, 229, 57, 53))
            )
        }
        updateUI()
    }

    private fun updateUI() {
        tvPointCount.text = "${vertices.size} points"
        val area = calculateAreaHectares(vertices)
        tvArea.text = if (vertices.size >= 3) "Field Area: %.4f ha".format(area) else "Field Area: — ha"
        btnConfirm.isEnabled = vertices.size >= 3
        tvHint.visibility = if (vertices.size < 3) View.VISIBLE else View.GONE
    }

    private fun calculateAreaHectares(coords: List<LatLng>): Double {
        if (coords.size < 3) return 0.0
        val R = 6371000.0
        val toRad = { d: Double -> d * Math.PI / 180 }
        var area = 0.0
        val n = coords.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            val xi = toRad(coords[i].longitude) * cos(toRad(coords[i].latitude))
            val yi = toRad(coords[i].latitude)
            val xj = toRad(coords[j].longitude) * cos(toRad(coords[j].latitude))
            val yj = toRad(coords[j].latitude)
            area += xi * yj - xj * yi
        }
        return abs(area) / 2 * R * R / 10000
    }

    private fun buildGeoJson(): Map<String, Any> {
        val positions = vertices.map { listOf(it.longitude, it.latitude) }.toMutableList()
        if (positions.isNotEmpty()) positions.add(positions[0])
        return mapOf(
            "type" to "Feature",
            "geometry" to mapOf("type" to "Polygon", "coordinates" to listOf(positions)),
            "properties" to emptyMap<String, Any>()
        )
    }

    private fun createSurveyAndProceed() {
        progressBar.visibility = View.VISIBLE
        btnConfirm.isEnabled = false

        val chmVisitNumber = intent.getIntExtra("chm_visit_number", 1)
        val chmCaseId      = intent.getStringExtra("chm_case_id")

        lifecycleScope.launch {
            try {
                // All survey types use the same POST surveys endpoint.
                // For CHM, visit number and case_id are stored in form_data and session.
                val res = ApiClient.service.createSurvey(CreateSurveyRequest(surveyType))
                if (res.isSuccessful && res.body() != null) {
                    val survey = res.body()!!

                    if (surveyType == "CHM") {
                        // Set CHM metadata in formData BEFORE startSurvey so the
                        // carry-over snapshot captures the correct chm_case_id
                        val effectiveCaseId = chmCaseId ?: survey.caseId
                        SurveySession.chmVisitNumber = chmVisitNumber
                        SurveySession.chmCaseId = effectiveCaseId
                        SurveySession.formData["chm_visit_number"] = chmVisitNumber
                        SurveySession.formData["chm_case_id"] = effectiveCaseId
                    }

                    // startSurvey resets formData but preserves CHM carry-over fields
                    SurveySession.startSurvey(surveyType, survey.id)
                    SurveySession.currentCaseId       = survey.caseId
                    SurveySession.polygonGeoJson      = buildGeoJson()
                    SurveySession.polygonAreaHectares = calculateAreaHectares(vertices)

                    val intent = Intent(this@PolygonMapActivity, SurveyTabsActivity::class.java)
                    intent.putExtra("survey_type", surveyType)
                    intent.putExtra("survey_id", survey.id)
                    intent.putExtra("farmer_verified", farmerVerified)
                    intent.putExtra("farmer_phone", farmerPhone)
                    if (surveyType == "CHM") {
                        intent.putExtra("chm_visit_number", chmVisitNumber)
                        intent.putExtra("chm_case_id", SurveySession.chmCaseId)
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this@PolygonMapActivity, "Failed to create survey. Check connection.", Toast.LENGTH_LONG).show()
                    btnConfirm.isEnabled = true
                }
            } catch (e: Exception) {
                Toast.makeText(this@PolygonMapActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                btnConfirm.isEnabled = true
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (mapReady) {
            val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }
}