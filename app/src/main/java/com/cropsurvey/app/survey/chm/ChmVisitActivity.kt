package com.cropsurvey.app.survey.chm

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cropsurvey.app.R
import com.cropsurvey.app.map.ChmBufferCheckActivity
import com.cropsurvey.app.map.PolygonMapActivity
import com.cropsurvey.app.models.Survey
import com.cropsurvey.app.network.ApiClient
import com.cropsurvey.app.utils.SurveySession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChmVisitActivity : AppCompatActivity() {

    // ── Views ─────────────────────────────────────────────────────────────────
    private lateinit var btnNewChm: Button

    // Case ID entry section
    private lateinit var etCaseId: EditText
    private lateinit var btnLoad: Button
    private lateinit var tvError: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutVisitTabs: LinearLayout

    // Search section
    private lateinit var btnToggleSearch: TextView
    private lateinit var layoutSearch: LinearLayout
    private lateinit var etSearchState: EditText
    private lateinit var etSearchDistrict: EditText
    private lateinit var etSearchSubDistrict: EditText
    private lateinit var etSearchFarmerName: EditText
    private lateinit var etSearchMobile: EditText
    private lateinit var btnSearch: Button
    private lateinit var progressSearch: ProgressBar
    private lateinit var layoutSearchResults: LinearLayout

    companion object {
        const val TOTAL_VISITS = 5
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chm_visit)

        bindViews()
        setupListeners()
    }

    private fun bindViews() {
        btnNewChm          = findViewById(R.id.btn_new_chm)
        etCaseId           = findViewById(R.id.et_case_id)
        btnLoad            = findViewById(R.id.btn_load)
        tvError            = findViewById(R.id.tv_error)
        progressBar        = findViewById(R.id.progress_bar)
        layoutVisitTabs    = findViewById(R.id.layout_visit_tabs)
        btnToggleSearch    = findViewById(R.id.btn_toggle_search)
        layoutSearch       = findViewById(R.id.layout_search)
        etSearchState      = findViewById(R.id.et_search_state)
        etSearchDistrict   = findViewById(R.id.et_search_district)
        etSearchSubDistrict = findViewById(R.id.et_search_subdistrict)
        etSearchFarmerName = findViewById(R.id.et_search_farmer_name)
        etSearchMobile     = findViewById(R.id.et_search_mobile)
        btnSearch          = findViewById(R.id.btn_search)
        progressSearch     = findViewById(R.id.progress_search)
        layoutSearchResults = findViewById(R.id.layout_search_results)
    }

    private fun setupListeners() {
        btnNewChm.setOnClickListener {
            startNewVisit(visitNumber = 1, existingCaseId = null)
        }

        btnLoad.setOnClickListener {
            val caseId = etCaseId.text.toString().trim()
            if (caseId.isEmpty()) {
                tvError.text = "Please enter a Case ID"
                tvError.visibility = View.VISIBLE
            } else {
                tvError.visibility = View.GONE
                loadChmVisits(caseId)
            }
        }

        // Toggle search panel
        btnToggleSearch.setOnClickListener {
            if (layoutSearch.visibility == View.GONE) {
                layoutSearch.visibility = View.VISIBLE
                btnToggleSearch.text = "▲ Hide Search"
            } else {
                layoutSearch.visibility = View.GONE
                btnToggleSearch.text = "🔍 Find Case ID by Farmer / Location"
            }
        }

        btnSearch.setOnClickListener { searchChmCases() }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private fun searchChmCases() {
        val state       = etSearchState.text.toString().trim().uppercase()
        val district    = etSearchDistrict.text.toString().trim().uppercase()
        val subDistrict = etSearchSubDistrict.text.toString().trim().uppercase()
        val farmerName  = etSearchFarmerName.text.toString().trim().lowercase()
        val mobile      = etSearchMobile.text.toString().trim()

        if (state.isEmpty() && district.isEmpty() && subDistrict.isEmpty()
            && farmerName.isEmpty() && mobile.isEmpty()) {
            Toast.makeText(this, "Enter at least one search field", Toast.LENGTH_SHORT).show()
            return
        }

        progressSearch.visibility = View.VISIBLE
        btnSearch.isEnabled = false
        layoutSearchResults.removeAllViews()

        lifecycleScope.launch {
            try {
                // Fetch all CHM surveys across all statuses
                val allChmSurveys = mutableListOf<Survey>()
                for (status in listOf("draft", "submitted", "approved", "rejected")) {
                    try {
                        val res = withContext(Dispatchers.IO) {
                            ApiClient.service.getSurveys(status = status, limit = 100, mine = false)
                        }
                        if (res.isSuccessful) {
                            allChmSurveys.addAll(
                                (res.body()?.data ?: emptyList()).filter { it.surveyType == "CHM" }
                            )
                        }
                    } catch (_: Exception) {}
                }

                // Filter by search fields against form_data
                val results = allChmSurveys.filter { survey ->
                    val fd = survey.formData
                    val fdState       = fd["state"]?.toString()?.uppercase() ?: ""
                    val fdDistrict    = fd["district"]?.toString()?.uppercase() ?: ""
                    val fdTehsil      = fd["tehsil"]?.toString()?.uppercase() ?: ""
                    val fdFarmer      = fd["farmer_name"]?.toString()?.lowercase() ?: ""
                    val fdMobile      = fd["farmer_mobile"]?.toString() ?: ""

                    (state.isEmpty()       || fdState.contains(state)) &&
                            (district.isEmpty()    || fdDistrict.contains(district)) &&
                            (subDistrict.isEmpty() || fdTehsil.contains(subDistrict)) &&
                            (farmerName.isEmpty()  || fdFarmer.contains(farmerName)) &&
                            (mobile.isEmpty()      || fdMobile.contains(mobile))
                }

                // Deduplicate by chm_case_id — show one card per case
                val caseMap = linkedMapOf<String, MutableList<Survey>>()
                results.forEach { survey ->
                    val cid = survey.formData["chm_case_id"]?.toString() ?: survey.caseId
                    caseMap.getOrPut(cid) { mutableListOf() }.add(survey)
                }

                progressSearch.visibility = View.GONE
                btnSearch.isEnabled = true

                if (caseMap.isEmpty()) {
                    val tv = TextView(this@ChmVisitActivity)
                    tv.text = "No CHM cases found matching your search."
                    tv.setPadding(0, 16, 0, 0)
                    layoutSearchResults.addView(tv)
                } else {
                    renderSearchResults(caseMap)
                }

            } catch (e: Exception) {
                progressSearch.visibility = View.GONE
                btnSearch.isEnabled = true
                Toast.makeText(this@ChmVisitActivity, "Search failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderSearchResults(caseMap: Map<String, List<Survey>>) {
        layoutSearchResults.removeAllViews()

        caseMap.forEach { (caseId, visits) ->
            // Pick the visit with the most form data to show farmer info
            val representativeSurvey = visits.maxByOrNull { it.formData.size } ?: return@forEach
            val fd = representativeSurvey.formData

            val farmerName = fd["farmer_name"]?.toString() ?: "—"
            val mobile     = fd["farmer_mobile"]?.toString() ?: "—"
            val state      = fd["state"]?.toString() ?: "—"
            val district   = fd["district"]?.toString() ?: "—"
            val tehsil     = fd["tehsil"]?.toString() ?: ""
            val visitCount = visits.size
            val approvedCount = visits.count { it.status == "approved" }

            // Card container
            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.setPadding(32, 24, 32, 24)
            card.setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
            card.layoutParams = lp

            // Case ID row — show all visit IDs (e.g. SQQRTSDX5T-1, SQQRTSDX5T-2)
            val visitIds = visits.sortedBy {
                (it.formData["chm_visit_number"] as? Double)?.toInt()
                    ?: it.formData["chm_visit_number"]?.toString()?.toIntOrNull() ?: 99
            }.joinToString("  ") { it.caseId }
            val tvCaseId = TextView(this)
            tvCaseId.text = "Case ID: $visitIds"
            tvCaseId.textSize = 13f
            tvCaseId.setTextColor(0xFF64748B.toInt())
            card.addView(tvCaseId)

            // Farmer name
            val tvFarmer = TextView(this)
            tvFarmer.text = farmerName
            tvFarmer.textSize = 16f
            tvFarmer.setTextColor(0xFF1E293B.toInt())
            tvFarmer.setPadding(0, 4, 0, 2)
            card.addView(tvFarmer)

            // Location
            val tvLocation = TextView(this)
            tvLocation.text = listOf(tehsil, district, state).filter { it.isNotEmpty() }.joinToString(" · ")
            tvLocation.textSize = 13f
            tvLocation.setTextColor(0xFF64748B.toInt())
            card.addView(tvLocation)

            // Mobile + visit count
            val tvMeta = TextView(this)
            tvMeta.text = "📱 $mobile   |   $approvedCount/$visitCount visits approved"
            tvMeta.textSize = 12f
            tvMeta.setTextColor(0xFF64748B.toInt())
            tvMeta.setPadding(0, 4, 0, 12)
            card.addView(tvMeta)

            // Copy + Use buttons row
            val btnRow = LinearLayout(this)
            btnRow.orientation = LinearLayout.HORIZONTAL
            val btnRowLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            btnRow.layoutParams = btnRowLp

            val btnCopy = Button(this)
            btnCopy.text = "Copy Case ID"
            btnCopy.textSize = 12f
            btnCopy.setBackgroundColor(0xFF64748B.toInt())
            btnCopy.setTextColor(0xFFFFFFFF.toInt())
            val copyLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { setMargins(0, 0, 8, 0) }
            btnCopy.layoutParams = copyLp
            btnCopy.setOnClickListener {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Case ID", caseId))
                // Also fill the Case ID field automatically
                etCaseId.setText(caseId)
                Toast.makeText(this, "Case ID copied & pasted!", Toast.LENGTH_SHORT).show()
                // Scroll up and collapse search
                layoutSearch.visibility = View.GONE
                btnToggleSearch.text = "🔍 Find Case ID by Farmer / Location"
            }
            btnRow.addView(btnCopy)

            val btnUse = Button(this)
            btnUse.text = "Load Visits →"
            btnUse.textSize = 12f
            btnUse.setBackgroundColor(0xFF1565C0.toInt())
            btnUse.setTextColor(0xFFFFFFFF.toInt())
            val useLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            btnUse.layoutParams = useLp
            btnUse.setOnClickListener {
                layoutSearch.visibility = View.GONE
                btnToggleSearch.text = "🔍 Find Case ID by Farmer / Location"
                etCaseId.setText(caseId)
                loadChmVisits(caseId)
            }
            btnRow.addView(btnUse)

            card.addView(btnRow)
            layoutSearchResults.addView(card)
        }
    }

    // ── Load visits by Case ID ────────────────────────────────────────────────

    private fun loadChmVisits(caseId: String) {
        progressBar.visibility = View.VISIBLE
        layoutVisitTabs.visibility = View.GONE
        tvError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val allSurveys = mutableListOf<Survey>()
                for (status in listOf("draft", "submitted", "approved", "rejected")) {
                    try {
                        val res = withContext(Dispatchers.IO) {
                            ApiClient.service.getSurveys(status = status, limit = 100)
                        }
                        if (res.isSuccessful) {
                            allSurveys.addAll(res.body()?.data ?: emptyList())
                        }
                    } catch (_: Exception) {}
                }

                // Accept both the base ID (SQQRTSDX5T) and any suffixed form (SQQRTSDX5T-2).
                // Strip trailing -N so the user can paste any visit's case_id and still load all visits.
                val baseLookup = caseId.replace(Regex("-\\d+$"), "")
                val chmVisits = allSurveys.filter { survey ->
                    survey.surveyType == "CHM" &&
                            (survey.formData["chm_case_id"]?.toString() == baseLookup ||
                                    survey.caseId.replace(Regex("-\\d+$"), "") == baseLookup)
                }.sortedBy { survey ->
                    (survey.formData["chm_visit_number"] as? Double)?.toInt()
                        ?: survey.formData["chm_visit_number"]?.toString()?.toIntOrNull()
                        ?: 99
                }

                progressBar.visibility = View.GONE

                if (chmVisits.isEmpty()) {
                    showError("No CHM visits found for Case ID: $caseId")
                } else {
                    renderVisitTabs(caseId, chmVisits, chmVisits)
                }
            } catch (e: Exception) {
                showError("Network error. Please try again.")
            }
        }
    }

    private fun renderVisitTabs(caseId: String, visits: List<Survey>, allVisits: List<Survey>) {
        layoutVisitTabs.removeAllViews()
        layoutVisitTabs.visibility = View.VISIBLE

        val visitMap = visits.associateBy { survey ->
            (survey.formData["chm_visit_number"] as? Double)?.toInt()
                ?: survey.formData["chm_visit_number"]?.toString()?.toIntOrNull()
                ?: 0
        }

        // Always use the base chm_case_id stored in form_data (e.g. "SQQRTSDX5T"),
        // NOT the typed caseId which may be a full case_id like "SQQRTSDX5T-1".
        // This is what gets sent to the backend when starting a new visit.
        val baseChmCaseId = visits.firstOrNull()
            ?.formData?.get("chm_case_id")?.toString()
            ?: caseId.replace(Regex("-\\d+$"), "")  // fallback: strip trailing -N

        val approvedNums = visitMap.filter { it.value.status == "approved" }.keys
        val nextVisitNum = (1..TOTAL_VISITS).firstOrNull { it !in approvedNums } ?: (TOTAL_VISITS + 1)

        // Header showing base case ID (strip suffix so it always reads e.g. "SQQRTSDX5T")
        val displayCaseBase = baseChmCaseId
        val tvHeader = TextView(this)
        tvHeader.text = "Case: $displayCaseBase"
        tvHeader.textSize = 13f
        tvHeader.setTextColor(0xFF64748B.toInt())
        tvHeader.setPadding(0, 0, 0, 8)
        layoutVisitTabs.addView(tvHeader)

        for (i in 1..TOTAL_VISITS) {
            val btn    = Button(this)
            val survey = visitMap[i]
            val status = survey?.status

            val isApproved  = status == "approved"
            val isSubmitted = status == "submitted"
            val isDraft     = status == "draft"
            val isNext      = i == nextVisitNum && survey == null
            val isFuture    = i > nextVisitNum && survey == null

            btn.text = when {
                isApproved  -> "✓  Visit $i — Approved"
                isSubmitted -> "⏳  Visit $i — Pending Review"
                isDraft     -> "✏️  Visit $i — Draft"
                isNext      -> "▶  Visit $i — Start Now"
                else        -> "🔒  Visit $i"
            }

            btn.isEnabled = !isFuture

            btn.setBackgroundColor(when {
                isApproved  -> 0xFF4CAF50.toInt()
                isSubmitted -> 0xFFFF9800.toInt()
                isDraft     -> 0xFF2196F3.toInt()
                isNext      -> 0xFF1565C0.toInt()
                else        -> 0xFFBDBDBD.toInt()
            })
            btn.setTextColor(0xFFFFFFFF.toInt())

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 8) }
            btn.layoutParams = lp

            val visitNum = i
            btn.setOnClickListener {
                when {
                    isApproved             -> openReadOnlyVisit(survey!!, visitNum)
                    isDraft || isSubmitted -> openExistingVisit(survey!!, visitNum)
                    isNext                 -> startNewVisit(visitNumber = visitNum, existingCaseId = baseChmCaseId, previousVisits = allVisits)
                }
            }
            layoutVisitTabs.addView(btn)
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    // Fields that carry over from visit to visit (static info about farmer/field)
    private val CARRY_OVER_FIELDS = listOf(
        "online_chm_id", "year", "season", "scheme", "others_scheme",
        "state", "district", "tehsil", "revenue_circle", "gram_panchayat", "village",
        "crop_name", "insurance_unit", "crop_variety", "sowing_method",
        "irrigation_type", "land_type",
        "farmer_name", "farmer_mobile", "farmer_application_no",
        "sowing_date", "expected_harvest_date",
        "field_area_polygon",
        "chm_case_id", "chm_visit_number"
    )

    private fun startNewVisit(visitNumber: Int, existingCaseId: String?, previousVisits: List<Survey> = emptyList()) {
        // Snapshot farmer verification BEFORE clearSession() wipes formData
        val farmerPhone    = SurveySession.formData["farmer_phone"]?.toString() ?: ""
        val farmerVerified = SurveySession.formData["farmer_verified"]?.toString() == "yes"

        SurveySession.clearSession()
        SurveySession.chmVisitNumber = visitNumber
        SurveySession.chmCaseId = existingCaseId

        // Pre-fill common fields from the most data-rich previous visit
        if (visitNumber > 1 && previousVisits.isNotEmpty()) {
            val bestPrevVisit = previousVisits
                .filter { it.status in listOf("approved", "submitted", "draft") }
                .maxByOrNull { it.formData.size }
            if (bestPrevVisit != null) {
                val prevFd = bestPrevVisit.formData
                CARRY_OVER_FIELDS.forEach { key ->
                    if (key != "chm_visit_number") {
                        prevFd[key]?.let { value -> SurveySession.formData[key] = value }
                    }
                }

                // ── Load the polygon from visit 1 into SurveySession ──────────
                // This lets ChmBufferCheckActivity show the existing field boundary
                // as a read-only view without requiring the user to redraw it.
                val visit1 = previousVisits
                    .filter { it.status in listOf("approved", "submitted", "draft") }
                    .minByOrNull {
                        (it.formData["chm_visit_number"] as? Double)?.toInt()
                            ?: it.formData["chm_visit_number"]?.toString()?.toIntOrNull()
                            ?: 99
                    }
                if (visit1?.polygonGeoJson != null) {
                    SurveySession.polygonGeoJson = visit1.polygonGeoJson
                }
            }
        }

        // Always set the correct visit number and case id
        SurveySession.formData["chm_visit_number"] = visitNumber
        existingCaseId?.let { SurveySession.formData["chm_case_id"] = it }

        // Restore farmer verification into the fresh session so SurveySession always has it
        if (farmerVerified && farmerPhone.isNotEmpty()) {
            SurveySession.formData["farmer_phone"]    = farmerPhone
            SurveySession.formData["farmer_verified"] = "yes"
        }

        if (visitNumber == 1) {
            // Visit 1 — user must draw the field polygon
            val intent = Intent(this, PolygonMapActivity::class.java)
            intent.putExtra("survey_type", "CHM")
            intent.putExtra("chm_visit_number", visitNumber)
            // Pass farmer verification forward so PolygonMapActivity can relay it
            // to SurveyFormActivity — without this the data is lost after startSurvey() resets formData
            intent.putExtra("farmer_phone", farmerPhone)
            intent.putExtra("farmer_verified", farmerVerified)
            startActivity(intent)
        } else {
            // Visit 2–5 — polygon already exists from visit 1.
            // Skip PolygonMapActivity entirely; go straight to the buffer check
            // which will display the polygon as read-only and then open the form.
            val intent = Intent(this, ChmBufferCheckActivity::class.java)
            intent.putExtra(ChmBufferCheckActivity.EXTRA_VISIT_NUMBER, visitNumber)
            existingCaseId?.let { intent.putExtra(ChmBufferCheckActivity.EXTRA_CASE_ID, it) }
            startActivity(intent)
        }
    }

    private fun openExistingVisit(survey: Survey, visitNumber: Int) {
        SurveySession.clearSession()
        SurveySession.chmVisitNumber = visitNumber
        SurveySession.chmCaseId = survey.formData["chm_case_id"]?.toString()
        SurveySession.formData.putAll(survey.formData.mapValues { it.value })

        val intent = Intent(this, com.cropsurvey.app.survey.SurveyTabsActivity::class.java)
        intent.putExtra("survey_type", "CHM")
        intent.putExtra("survey_id", survey.id)
        intent.putExtra("chm_visit_number", visitNumber)
        intent.putExtra("chm_case_id", SurveySession.chmCaseId)
        intent.putExtra("is_edit_mode", survey.status == "draft")
        startActivity(intent)
    }

    private fun openReadOnlyVisit(survey: Survey, visitNumber: Int) {
        val intent = Intent(this, ChmVisitReadOnlyActivity::class.java)
        intent.putExtra("visit_number", visitNumber)
        intent.putExtra("survey_id", survey.id)
        intent.putExtra("case_id", survey.formData["chm_case_id"]?.toString() ?: survey.caseId)
        startActivity(intent)
    }

    private fun showError(msg: String) {
        progressBar.visibility = View.GONE
        tvError.text = msg
        tvError.visibility = View.VISIBLE
    }
}