package com.cropsurvey.app.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
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
import com.cropsurvey.app.models.CreateSurveyRequest
import com.cropsurvey.app.network.ApiClient
import com.cropsurvey.app.survey.SurveyTabsActivity
import com.cropsurvey.app.utils.GeoBufferHelper
import com.cropsurvey.app.utils.MockLocationDetector
import com.cropsurvey.app.utils.SurveySession
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * ChmBufferCheckActivity — shown for CHM Visit 2, 3, 4, 5.
 *
 * Responsibilities:
 *  1. Display the existing polygon from the previous visit on a Google Map.
 *  2. Track live GPS and show how far the user is from the 50 m buffer.
 *  3. Once inside the 50 m buffer:
 *     - Remove the warning overlay
 *     - Show a green "You are within the boundary!" card
 *     - Unlock the "Start Visit Survey" button
 *  4. On confirming, create the survey row on the server and open SurveyFormActivity.
 *
 * NOTE: No polygon drawing happens here — the existing polygon from Visit 1 is
 * automatically copied into SurveySession.polygonGeoJson by the caller before
 * starting this activity.
 */
class ChmBufferCheckActivity : BaseActivity(), OnMapReadyCallback {

    // ── Views ────────────────────────────────────────────────────────────────
    private lateinit var tvVisitLabel:       TextView
    private lateinit var tvCaseId:           TextView
    private lateinit var tvDistancePill:     TextView
    private lateinit var tvDistanceDetail:   TextView
    private lateinit var layoutOutside:      View
    private lateinit var layoutInside:       View
    private lateinit var btnStartLocked:     Button
    private lateinit var btnStart:           Button
    private lateinit var progressBar:        ProgressBar

    // ── Map ──────────────────────────────────────────────────────────────────
    private var googleMap: GoogleMap? = null
    private var mapReady = false
    private var existingPolygon: com.google.android.gms.maps.model.Polygon? = null
    private var bufferPolygon:   com.google.android.gms.maps.model.Polygon? = null
    private var youAreHereMarker: Marker? = null
    private var currentLatLng: LatLng? = null
    private var isProgrammaticMove = false

    // ── GPS ──────────────────────────────────────────────────────────────────
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    // ── State ────────────────────────────────────────────────────────────────
    private var isInsideBuffer = false
    private var visitNumber = 2
    private var chmCaseId: String? = null

    companion object {
        private const val BUFFER_METERS     = 50.0
        private const val EARTH_RADIUS      = 6371000.0
        const val EXTRA_VISIT_NUMBER        = "chm_visit_number"
        const val EXTRA_CASE_ID             = "chm_case_id"
    }

    // ── Permission launcher ──────────────────────────────────────────────────
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
                || perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) { if (mapReady) startLocationUpdates() }
        else {
            AlertDialog.Builder(this)
                .setTitle("Location Required")
                .setMessage("GPS is required to verify you are at the survey site.")
                .setPositiveButton("OK") { _, _ -> finish() }
                .setCancelable(false).show()
        }
    }

    // ────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chm_buffer_check)

        if (MockLocationDetector.isMockLocation(this)) {
            AlertDialog.Builder(this)
                .setTitle("Security Violation")
                .setMessage("Mock location detected. Real GPS is required for surveys.")
                .setPositiveButton("OK") { _, _ -> finish() }
                .setCancelable(false).show()
            return
        }

        visitNumber = intent.getIntExtra(EXTRA_VISIT_NUMBER, 2)
        chmCaseId   = intent.getStringExtra(EXTRA_CASE_ID)

        bindViews()
        setupHeader()
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

        checkLocationPermission()
    }

    private fun bindViews() {
        tvVisitLabel     = findViewById(R.id.tv_visit_label)
        tvCaseId         = findViewById(R.id.tv_case_id)
        tvDistancePill   = findViewById(R.id.tv_distance_pill)
        tvDistanceDetail = findViewById(R.id.tv_distance_detail)
        layoutOutside    = findViewById(R.id.layout_outside_buffer)
        layoutInside     = findViewById(R.id.layout_inside_buffer)
        btnStartLocked   = findViewById(R.id.btn_start_survey_locked)
        btnStart         = findViewById(R.id.btn_start_survey)
        progressBar      = findViewById(R.id.progress_bar)
    }

    private fun setupHeader() {
        tvVisitLabel.text = "CHM — Visit $visitNumber"
        tvCaseId.text     = "Case: ${chmCaseId ?: "—"}"
    }

    private fun setupButtons() {
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        btnStart.setOnClickListener {
            if (isInsideBuffer) createVisitAndProceed()
        }
    }

    private fun checkLocationPermission() {
        val fine   = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            if (mapReady) startLocationUpdates()
        } else {
            locationPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    // ── Map ──────────────────────────────────────────────────────────────────

    override fun onMapReady(map: GoogleMap) {
        mapReady  = true
        googleMap = map
        map.mapType = GoogleMap.MAP_TYPE_HYBRID
        map.uiSettings.isZoomControlsEnabled     = true
        map.uiSettings.isMyLocationButtonEnabled = false

        // Draw the existing polygon from Visit 1
        drawExistingPolygon()

        // Start GPS once map is ready
        val fine   = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
    }

    /**
     * Parse SurveySession.polygonGeoJson and render the polygon on the map.
     * Also draws a 50 m dashed buffer ring around the polygon's centroid so
     * the surveyor can see the target zone even before GPS locks on.
     */
    @Suppress("UNCHECKED_CAST")
    private fun drawExistingPolygon() {
        val map  = googleMap ?: return
        val geo  = SurveySession.polygonGeoJson ?: return

        try {
            val geometry = when (geo["type"]) {
                "Feature" -> geo["geometry"] as? Map<String, Any?> ?: return
                else      -> geo
            }
            val coords = (geometry["coordinates"] as? List<*>)
                ?.firstOrNull() as? List<*> ?: return

            val latLngs = coords.mapNotNull { pt ->
                val pair = pt as? List<*> ?: return@mapNotNull null
                val lng  = (pair[0] as? Number)?.toDouble() ?: return@mapNotNull null
                val lat  = (pair[1] as? Number)?.toDouble() ?: return@mapNotNull null
                LatLng(lat, lng)
            }

            if (latLngs.size < 3) return

            // Draw the field boundary (same green as dashboard)
            existingPolygon = map.addPolygon(
                PolygonOptions()
                    .addAll(latLngs)
                    .strokeColor(Color.parseColor("#16A34A"))
                    .strokeWidth(4f)
                    .fillColor(Color.argb(50, 22, 163, 74))
            )

            // Draw the 50 m buffer as a dashed circle around the centroid
            val centroid = centroidOf(latLngs)
            drawBufferRing(centroid)

            // Zoom the camera to show the polygon
            val bounds = LatLngBounds.builder().apply { latLngs.forEach { include(it) } }.build()
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))

        } catch (e: Exception) {
            // Polygon parsing failed — user can still proceed; buffer check will use GeoBufferHelper
        }
    }

    /**
     * Draw the 50 m buffer ring as a light-green circle around the given centre.
     * This gives the surveyor a visual target to walk toward.
     */
    private fun drawBufferRing(centre: LatLng) {
        val map = googleMap ?: return
        bufferPolygon?.remove()
        map.addCircle(
            CircleOptions()
                .center(centre)
                .radius(BUFFER_METERS)
                .strokeColor(Color.parseColor("#22C55E"))
                .strokeWidth(2.5f)
                .fillColor(Color.argb(25, 34, 197, 94))
        )
    }

    // ── GPS ──────────────────────────────────────────────────────────────────

    private fun startLocationUpdates() {
        if (!mapReady) return
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                onLocationUpdate(LatLng(loc.latitude, loc.longitude))
            }
        }

        fusedLocationClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())

        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null && currentLatLng == null) {
                onLocationUpdate(LatLng(loc.latitude, loc.longitude))
            }
        }
    }

    private fun onLocationUpdate(latLng: LatLng) {
        currentLatLng = latLng
        updateYouAreHereMarker(latLng)
        evaluateBuffer(latLng)
    }

    private fun updateYouAreHereMarker(latLng: LatLng) {
        val map = googleMap ?: return

        // Enable the native blue dot (same as Google Maps) — no custom marker needed
        val fine   = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            if (!map.isMyLocationEnabled) {
                map.isMyLocationEnabled = true
                map.uiSettings.isMyLocationButtonEnabled = false // hide the re-centre button
            }
        }

        // Remove any legacy custom marker
        youAreHereMarker?.remove()
        youAreHereMarker = null

        // Pan to keep the user's position visible without interrupting deliberate zoom
        if (!isProgrammaticMove) {
            isProgrammaticMove = true
            map.animateCamera(
                CameraUpdateFactory.newLatLng(latLng),
                300,
                object : GoogleMap.CancelableCallback {
                    override fun onFinish() { isProgrammaticMove = false }
                    override fun onCancel() { isProgrammaticMove = false }
                }
            )
        }
    }

    /**
     * Core logic: check if user is within BUFFER_METERS of the polygon.
     * Uses GeoBufferHelper (which reads SurveySession.polygonGeoJson) — same as
     * PhotoCaptureActivity does for Visit 1.
     *
     * When inside:
     *  - Hide the warning card, show the green card
     *  - Dismiss the map overlay (polygon stays but warning strip is gone)
     *  - Enable the Start button
     *
     * When outside:
     *  - Show the warning with exact distance
     *  - Keep the Start button locked
     */
    private fun evaluateBuffer(latLng: LatLng) {
        val distanceOutside = GeoBufferHelper.distanceOutsideBuffer(latLng.latitude, latLng.longitude)
        val inside = distanceOutside <= 0.0

        // Update the floating distance pill
        tvDistancePill.text = if (inside) {
            "✅ Inside boundary"
        } else {
            val meters = distanceOutside.toInt()
            "📍 ${meters} m from boundary"
        }
        tvDistancePill.setBackgroundColor(
            if (inside) Color.parseColor("#CC16A34A") else Color.parseColor("#CC0F172A")
        )

        if (inside && !isInsideBuffer) {
            // ── Transition: outside → inside ──────────────────────────────
            isInsideBuffer = true
            transitionToInsideState()
        } else if (!inside && isInsideBuffer) {
            // ── Edge case: user walked back outside ────────────────────────
            // Keep the green state — don't punish them for a momentary GPS drift.
            // If they genuinely left, they can use the back button.
        } else if (!inside) {
            val meters = distanceOutside.toInt()
            tvDistanceDetail.text = "You are ~${meters} m outside the 50 m boundary. Walk closer."
        }
    }

    /**
     * Animate the UI from the "outside" state to the "inside" state.
     * The map stays visible (surveyor can still see the polygon) but the
     * red warning strip is replaced by a green confirmation card.
     */
    private fun transitionToInsideState() {
        // Fade out the outside card, fade in the inside card
        layoutOutside.animate().alpha(0f).setDuration(300).withEndAction {
            layoutOutside.visibility = View.GONE
        }.start()

        layoutInside.alpha  = 0f
        layoutInside.visibility = View.VISIBLE
        layoutInside.animate().alpha(1f).setDuration(400).start()

        // Update the pill colour (already green from evaluateBuffer)
        tvDistancePill.text = "✅ You are within the survey boundary"
    }

    // ── Create survey row and navigate ───────────────────────────────────────

    private fun createVisitAndProceed() {
        progressBar.visibility = View.VISIBLE
        btnStart.isEnabled = false

        lifecycleScope.launch {
            try {
                // Create the survey row — pass the shared chm_case_id and visit number
                // so the backend can generate a linked case_id (e.g. SQQRTSDX5T-2, SQQRTSDX5T-3)
                // instead of a brand new random case_id for every visit.
                val body = CreateSurveyRequest(
                    surveyType       = "CHM",
                    chmCaseId        = chmCaseId,
                    chmVisitNumber   = visitNumber
                )
                val res  = ApiClient.service.createSurvey(body)

                if (res.isSuccessful && res.body() != null) {
                    val survey = res.body()!!

                    // Update session — polygonGeoJson is already loaded by the caller
                    SurveySession.startSurvey("CHM", survey.id)
                    SurveySession.currentCaseId      = survey.caseId
                    SurveySession.chmVisitNumber     = visitNumber
                    SurveySession.chmCaseId          = chmCaseId
                    SurveySession.formData["chm_visit_number"] = visitNumber
                    SurveySession.formData["chm_case_id"]      = chmCaseId

                    val intent = Intent(this@ChmBufferCheckActivity, SurveyTabsActivity::class.java)
                    intent.putExtra("survey_type",       "CHM")
                    intent.putExtra("survey_id",         survey.id)
                    intent.putExtra("chm_visit_number",  visitNumber)
                    intent.putExtra("chm_case_id",       chmCaseId)
                    startActivity(intent)
                    // Don't finish() — user can press back to abort
                } else {
                    Toast.makeText(this@ChmBufferCheckActivity,
                        "Failed to create survey. Check connection.", Toast.LENGTH_LONG).show()
                    btnStart.isEnabled = true
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChmBufferCheckActivity,
                    "Error: ${e.message}", Toast.LENGTH_LONG).show()
                btnStart.isEnabled = true
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        if (mapReady) {
            val fine   = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
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

    // ── Geometry helpers ─────────────────────────────────────────────────────

    private fun centroidOf(points: List<LatLng>): LatLng {
        val lat = points.map { it.latitude }.average()
        val lng = points.map { it.longitude }.average()
        return LatLng(lat, lng)
    }
}