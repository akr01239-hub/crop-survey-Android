package com.cropsurvey.app.config

import android.content.Context
import androidx.annotation.ColorInt
import android.graphics.Color
import com.cropsurvey.app.R

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║           CROP SURVEY APP — CENTRAL CONFIGURATION           ║
 * ║   Change anything here and it reflects across the whole app ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
object AppConfig {

    // ─── App Identity ────────────────────────────────────────────
    const val APP_NAME = "Crop Survey"
    const val APP_VERSION = "1.0.0"
    const val PACKAGE_NAME = "com.cropsurvey.app"

    // ─── Supabase Credentials ────────────────────────────────────
    const val SUPABASE_URL = "https://ahgzfmzjzispkwwbvijv.supabase.co"
    const val SUPABASE_ANON_KEY = "sb_publishable_7J1y2CNhBJluUq4DLr1rig_9Z4rRzSO"

    // ─── API Backend URL ─────────────────────────────────────────
    const val API_BASE_URL = "https://crop-survey-api.onrender.com"

    // ─── Google Maps API Key ──────────────────────────────────────
    const val GOOGLE_MAPS_API_KEY = "AIzaSyBlfD24fvfHS4agCYkrxg4v_SQWgnYuNxA"

    // ─── Colors ───────────────────────────────────────────────────
    object Colors {
        const val PRIMARY       = "#2563EB"
        const val SUCCESS       = "#16A34A"
        const val WARNING       = "#D97706"
        const val DANGER        = "#DC2626"
        const val BACKGROUND    = "#F7F9FB"
        const val SURFACE       = "#FFFFFF"
        const val BORDER        = "#E4EAF0"
        const val TEXT_PRIMARY  = "#0F172A"
        const val TEXT_MUTED    = "#64748B"
    }

    // ─── GPS Settings ─────────────────────────────────────────────
    const val GPS_MIN_ACCURACY_METERS = 50f
    const val GPS_BLOCK_ACCURACY_METERS = 500f
    const val GPS_TIMEOUT_SECONDS = 10
    const val GPS_UPDATE_INTERVAL_MS = 5000L

    // ─── Auto-save ────────────────────────────────────────────────
    const val AUTO_SAVE_INTERVAL_MS = 15_000L

    // ─── Offline Queue ────────────────────────────────────────────
    const val MAX_RETRY_COUNT = 5
    const val QUEUE_RETRY_DELAY_MS = 3000L

    // ─── Photo Settings ───────────────────────────────────────────
    const val PHOTO_QUALITY = 85
    const val PHOTO_MAX_WIDTH = 1920
    const val PHOTO_BUCKET = "survey-photos"

    // ─── Survey Photo Requirements (use context for localized labels) ─
    fun getCLSPhotos(ctx: Context, farmerAvailable: String? = null): List<PhotoRequirement> {
        val base = listOf(
            PhotoRequirement("sw_corner",              ctx.getString(R.string.photo_sw_corner_chm),   ctx.getString(R.string.photo_sw_corner_chm_instruction),   required = true),
            PhotoRequirement("closeup_affected_area",  ctx.getString(R.string.photo_closeup_affected_area), ctx.getString(R.string.photo_closeup_affected_area_instruction), required = true),
            PhotoRequirement("nadir_view",             ctx.getString(R.string.photo_nadir),           ctx.getString(R.string.photo_nadir_instruction),           required = true),
            PhotoRequirement("leaf_image",             ctx.getString(R.string.photo_leaf),            ctx.getString(R.string.photo_leaf_instruction),            required = true),
            PhotoRequirement("survey_form",            ctx.getString(R.string.photo_survey_form),     ctx.getString(R.string.photo_survey_form_instruction),     required = true),
            PhotoRequirement("land_record",            ctx.getString(R.string.photo_land_record),     ctx.getString(R.string.photo_land_record_instruction),     required = true),
            PhotoRequirement("id_proof",               ctx.getString(R.string.photo_id_proof),        ctx.getString(R.string.photo_id_proof_instruction),        required = true),
            PhotoRequirement("image_with_farmer",      ctx.getString(R.string.photo_with_farmer),     ctx.getString(R.string.photo_with_farmer_instruction),     required = true),
            PhotoRequirement("image_with_official",    ctx.getString(R.string.photo_with_official),   ctx.getString(R.string.photo_with_official_instruction),   required = true),
            PhotoRequirement("field_image_1",          ctx.getString(R.string.photo_field_north),     ctx.getString(R.string.photo_field_north_instruction),     required = true),
            PhotoRequirement("field_image_2",          ctx.getString(R.string.photo_field_east),      ctx.getString(R.string.photo_field_east_instruction),      required = true),
            PhotoRequirement("field_image_3",          ctx.getString(R.string.photo_field_south),     ctx.getString(R.string.photo_field_south_instruction),     required = true),
            PhotoRequirement("field_image_4",          ctx.getString(R.string.photo_field_west),      ctx.getString(R.string.photo_field_west_instruction),      required = true),
        )
        return if (farmerAvailable == "no") base + getCLSRepresentativeIdPhoto(ctx) else base
    }

    /** Conditional photo requirement, appended when Section 4 "Farmer Available" = No */
    fun getCLSRepresentativeIdPhoto(ctx: Context): PhotoRequirement =
        PhotoRequirement("representative_id_photo", ctx.getString(R.string.field_representative_id_photo), null, required = true)

    fun getCHMPhotos(ctx: Context): List<PhotoRequirement> = listOf(
        PhotoRequirement("sw_corner",            ctx.getString(R.string.photo_sw_corner_chm),      null,                                                         required = true),
        PhotoRequirement("field_image_1",        ctx.getString(R.string.photo_field_north),        null,                                                         required = true),
        PhotoRequirement("field_image_2",        ctx.getString(R.string.photo_field_east),         null,                                                         required = true),
        PhotoRequirement("field_image_3",        ctx.getString(R.string.photo_field_south),        null,                                                         required = true),
        PhotoRequirement("field_image_4",        ctx.getString(R.string.photo_field_west),         null,                                                         required = true),
        PhotoRequirement("nadir_view",           ctx.getString(R.string.photo_nadir),              ctx.getString(R.string.photo_nadir_instruction),              required = true),
        PhotoRequirement("leaf_image",           ctx.getString(R.string.photo_leaf),               null,                                                         required = true),
        PhotoRequirement("grain_image",          ctx.getString(R.string.photo_grain_image),        ctx.getString(R.string.photo_grain_image_instruction),        required = true),
        PhotoRequirement("weight_image_biomass", ctx.getString(R.string.photo_biomass_weight),     ctx.getString(R.string.photo_biomass_weight_instruction),     required = true),
        PhotoRequirement("grain_weight_image",   ctx.getString(R.string.photo_grain_weight),       ctx.getString(R.string.photo_grain_weight_instruction),       required = true),
    )

    fun getCCEPhotos(ctx: Context): List<PhotoRequirement> {
        val base = listOf(
        PhotoRequirement("sw_corner",              "SW Corner",               "Photo from SW corner showing crop standing in plot",            required = true),
        PhotoRequirement("nadir_view",             "Nadir View",              "Hold phone directly overhead, camera pointing straight down",   required = true),
        PhotoRequirement("leaf_closeup",           "Leaf Photo Closeup",      "Close-up photo of crop leaf filling the frame",                 required = true),
        PhotoRequirement("plot_marking",           "Plot Marking",            "Four corner pegs/stakes marking the CCE plot",                  required = true),
        PhotoRequirement("cut_plot",               "Cut Plot",                "Photo of harvested/cut plot area",                              required = true),
        PhotoRequirement("biomass_weight",         "Biomass Weight",          "Total fresh biomass on calibrated weighing scale (kg)",          required = true),
        PhotoRequirement("threshing_photo",        "Threshing",               "Crop being threshed (manual/mechanical)",                       required = true),
        PhotoRequirement("wet_weight",             "Wet Weight",              "Wet grain on weighing scale (kg)",                              required = true),
        PhotoRequirement("moisture_reading",       "Moisture % Reading",      "Moisture meter showing % moisture reading of grain sample",     required = true),
        PhotoRequirement("dry_grain_weight",       "Dry Grain Weight",        "Dried grain on weighing scale — if moisture meter not available", required = true),
        PhotoRequirement("witness_photo",          "Witness at Site",         "Photo of IC representative / Patwari / Farmer at CCE site",     required = true),
        PhotoRequirement("neighbor_field",         "Neighbor Field Photo",    "Photo of the neighboring field for reference",                  required = true),
        PhotoRequirement("representative_id_photo","Farmer Representative ID","Photo of farmer representative's ID proof",                     required = true),
        PhotoRequirement("other_remains",          "If Any Remains",          "Any additional relevant photos",                                required = false),
        )
        return base
    }

    // Keep old static lists for backward compat — callers should migrate to context versions
    val CLS_PHOTOS get() = getCLSPhotos_static().let { base ->
        if (com.cropsurvey.app.utils.SurveySession.formData["farmer_available"]?.toString() == "no")
            base + PhotoRequirement("representative_id_photo", "Farmer Representative ID", null, required = true)
        else base
    }
    val CHM_PHOTOS get() = getCHMPhotos_static()
    val CCE_PHOTOS get() = getCCEPhotos_static()

    private fun getCLSPhotos_static() = listOf(
        PhotoRequirement("sw_corner",             "SW Corner",               "Stand at south-west corner of the field",                    required = true),
        PhotoRequirement("closeup_affected_area", "Closeup Affected Area Photo", "Close-up of the affected crop/field area",               required = true),
        PhotoRequirement("nadir_view",            "Nadir View",              "Hold phone directly overhead, camera pointing straight down", required = true),
        PhotoRequirement("leaf_image",            "Leaf Close-up",           "Fill frame with a representative leaf",                      required = true),
        PhotoRequirement("survey_form",           "Survey Form",             "Lay form flat, capture all 4 corners",                       required = true),
        PhotoRequirement("land_record",           "Land Record (7/12)",      "Document must be fully visible",                             required = true),
        PhotoRequirement("id_proof",              "Farmer ID Proof",         "All text must be legible",                                   required = true),
        PhotoRequirement("image_with_farmer",     "Image with Farmer",       "Farmer must be clearly visible in frame",                    required = true),
        PhotoRequirement("image_with_official",   "Image with Govt Official", "Government officer must be visible",                        required = true),
        PhotoRequirement("field_image_1",         "Field — North",           "Stand at south boundary, face north",                        required = true),
        PhotoRequirement("field_image_2",         "Field — East",            "Stand at west boundary, face east",                          required = true),
        PhotoRequirement("field_image_3",         "Field — South",           "Stand at north boundary, face south",                        required = true),
        PhotoRequirement("field_image_4",         "Field — West",            "Stand at east boundary, face west",                          required = true),
    )
    private fun getCHMPhotos_static() = listOf(
        PhotoRequirement("sw_corner",            "SW Corner",            instruction = null,                             required = true),
        PhotoRequirement("field_image_1",        "Field — North",        instruction = null,                             required = true),
        PhotoRequirement("field_image_2",        "Field — East",         instruction = null,                             required = true),
        PhotoRequirement("field_image_3",        "Field — South",        instruction = null,                             required = true),
        PhotoRequirement("field_image_4",        "Field — West",         instruction = null,                             required = true),
        PhotoRequirement("nadir_view",           "Nadir View",           "Hold phone directly overhead",                 required = true),
        PhotoRequirement("leaf_image",           "Leaf Close-up",        instruction = null,                             required = true),
        PhotoRequirement("grain_image",          "Grain Image",          "Close-up of grain/fruit",                      required = true),
        PhotoRequirement("weight_image_biomass", "Biomass Weight Photo", "Photo of biomass on weighing scale",            required = true),
        PhotoRequirement("grain_weight_image",   "Grain Weight Photo",   "Photo of grain on weighing scale",              required = true),
    )
    private fun getCCEPhotos_static() = listOf(
        PhotoRequirement("sw_corner",              "SW Corner",               "Photo from SW corner showing crop standing in plot",            required = true),
        PhotoRequirement("nadir_view",             "Nadir View",              "Hold phone directly overhead, camera pointing straight down",   required = true),
        PhotoRequirement("leaf_closeup",           "Leaf Photo Closeup",      "Close-up photo of crop leaf filling the frame",                 required = true),
        PhotoRequirement("plot_marking",           "Plot Marking",            "Four corner pegs/stakes marking the CCE plot",                  required = true),
        PhotoRequirement("cut_plot",               "Cut Plot",                "Photo of harvested/cut plot area",                              required = true),
        PhotoRequirement("biomass_weight",         "Biomass Weight",          "Total fresh biomass on calibrated weighing scale (kg)",          required = true),
        PhotoRequirement("threshing_photo",        "Threshing",               "Crop being threshed (manual/mechanical)",                       required = true),
        PhotoRequirement("wet_weight",             "Wet Weight",              "Wet grain on weighing scale (kg)",                              required = true),
        PhotoRequirement("moisture_reading",       "Moisture % Reading",      "Moisture meter showing % moisture reading of grain sample",     required = true),
        PhotoRequirement("dry_grain_weight",       "Dry Grain Weight",        "Dried grain on weighing scale — if moisture meter not available", required = true),
        PhotoRequirement("witness_photo",          "Witness at Site",         "Photo of IC representative / Patwari / Farmer at CCE site",     required = true),
        PhotoRequirement("neighbor_field",         "Neighbor Field Photo",    "Photo of the neighboring field for reference",                  required = true),
        PhotoRequirement("representative_id_photo","Farmer Representative ID","Photo of farmer representative's ID proof",                     required = true),
        PhotoRequirement("other_remains",          "If Any Remains",          "Any additional relevant photos",                                required = false),
    )

    fun getPhotosForType(type: String): List<PhotoRequirement> = when (type) {
        "CLS" -> CLS_PHOTOS
        "CHM" -> CHM_PHOTOS
        "CCE" -> CCE_PHOTOS
        else  -> emptyList()
    }

    fun getPhotosForType(ctx: Context, type: String): List<PhotoRequirement> = when (type) {
        "CLS" -> getCLSPhotos(ctx, com.cropsurvey.app.utils.SurveySession.formData["farmer_available"]?.toString())
        "CHM" -> getCHMPhotos(ctx)
        "CCE" -> getCCEPhotos(ctx)
        else  -> emptyList()
    }

    // ─── Dropdown Options (localized via context) ─────────────────
    fun getSeasons(ctx: Context) = listOf(
        ctx.getString(R.string.season_kharif),
        ctx.getString(R.string.season_rabi),
        ctx.getString(R.string.season_zaid)
    )
    fun getSchemes(ctx: Context) = listOf(
        ctx.getString(R.string.scheme_rwbcis),
        ctx.getString(R.string.scheme_pmfby),
        ctx.getString(R.string.scheme_wbcis),
        ctx.getString(R.string.scheme_others)
    )
    fun getCrops(ctx: Context) = listOf(
        ctx.getString(R.string.crop_wheat), ctx.getString(R.string.crop_rice),
        ctx.getString(R.string.crop_mango), ctx.getString(R.string.crop_soybean),
        ctx.getString(R.string.crop_cotton), ctx.getString(R.string.crop_sugarcane),
        ctx.getString(R.string.crop_banana), ctx.getString(R.string.crop_tomato),
        ctx.getString(R.string.crop_potato), ctx.getString(R.string.crop_onion),
        ctx.getString(R.string.crop_sorghum), ctx.getString(R.string.crop_maize),
        ctx.getString(R.string.crop_groundnut), ctx.getString(R.string.crop_sunflower),
        ctx.getString(R.string.crop_gram), ctx.getString(R.string.crop_tur),
        ctx.getString(R.string.crop_moong), ctx.getString(R.string.crop_urad),
        ctx.getString(R.string.crop_linseed), ctx.getString(R.string.crop_mustard),
        ctx.getString(R.string.crop_grapes), ctx.getString(R.string.crop_pomegranate),
        ctx.getString(R.string.crop_orange), ctx.getString(R.string.crop_other)
    )
    fun getCropStages(ctx: Context) = listOf(
        ctx.getString(R.string.stage_sowing), ctx.getString(R.string.stage_germination),
        ctx.getString(R.string.stage_vegetative), ctx.getString(R.string.stage_tillering),
        ctx.getString(R.string.stage_flowering), ctx.getString(R.string.stage_grain_filling),
        ctx.getString(R.string.stage_maturity), ctx.getString(R.string.stage_harvesting)
    )
    fun getCroppingPatterns(ctx: Context) = listOf(
        ctx.getString(R.string.pattern_single),
        ctx.getString(R.string.pattern_mixed),
        ctx.getString(R.string.pattern_intercrop)
    )
    fun getIrrigationTypes(ctx: Context) = listOf(
        ctx.getString(R.string.irrigation_irrigated),
        ctx.getString(R.string.irrigation_rainfed),
        ctx.getString(R.string.irrigation_partial)
    )
    fun getLandTypes(ctx: Context) = listOf(
        ctx.getString(R.string.land_plain), ctx.getString(R.string.land_hilly),
        ctx.getString(R.string.land_undulating), ctx.getString(R.string.land_coastal),
        ctx.getString(R.string.land_wetland)
    )
    fun getCauseOfEvent(ctx: Context) = listOf(
        ctx.getString(R.string.cause_heavy_rain), ctx.getString(R.string.cause_drought),
        ctx.getString(R.string.cause_hailstorm), ctx.getString(R.string.cause_flood),
        ctx.getString(R.string.cause_cyclone), ctx.getString(R.string.cause_pest),
        ctx.getString(R.string.cause_fire), ctx.getString(R.string.cause_landslide),
        ctx.getString(R.string.cause_cold_wave), ctx.getString(R.string.cause_heat_wave),
        ctx.getString(R.string.cause_lightning), ctx.getString(R.string.cause_other)
    )
    fun getOnfieldConditions(ctx: Context) = listOf(
        ctx.getString(R.string.condition_excellent), ctx.getString(R.string.condition_good),
        ctx.getString(R.string.condition_fair), ctx.getString(R.string.condition_poor),
        ctx.getString(R.string.condition_complete_loss)
    )
    fun getAccountTypes(ctx: Context) = listOf(
        ctx.getString(R.string.account_saving),
        ctx.getString(R.string.account_current),
        ctx.getString(R.string.account_kcc)
    )
    fun getInsuranceUnits(ctx: Context) = listOf(
        ctx.getString(R.string.unit_ri_circle),
        ctx.getString(R.string.unit_village),
        ctx.getString(R.string.unit_district),
        ctx.getString(R.string.unit_block)
    )

    // ─── Static fallbacks (for backward compat) ───────────────────
    val YEARS = listOf("2022-2023", "2023-2024", "2024-2025", "2025-2026", "2026-2027")
    val SEASONS = listOf("Kharif", "Rabi", "Zaid")
    val SCHEMES = listOf("RWBCIS", "PMFBY", "WBCIS", "Others")
    val CROPS = listOf(
        "Wheat", "Rice", "Mango", "Soybean", "Cotton", "Sugarcane",
        "Banana", "Tomato", "Potato", "Onion", "Sorghum", "Maize",
        "Groundnut", "Sunflower", "Gram", "Tur (Arhar)", "Moong",
        "Urad", "Linseed", "Mustard", "Grapes", "Pomegranate", "Orange", "Other"
    )
    val INSURANCE_UNITS = listOf("RICircle Level", "Village Level", "District Level", "Block Level")
    val CROP_STAGES = listOf("Sowing", "Germination", "Vegetative", "Tillering", "Flowering", "Grain Filling", "Maturity", "Harvesting")
    val CROPPING_PATTERNS = listOf("Single", "Mixed", "Intercrop")
    val IRRIGATION_TYPES = listOf("Irrigated", "Rainfed", "Partially Irrigated")
    val LAND_TYPES = listOf("Plain", "Hilly", "Undulating", "Coastal", "Wetland")
    val CAUSE_OF_EVENT = listOf("Heavy Rainfall", "Drought", "Hailstorm", "Flood", "Cyclone", "Pest & Disease", "Fire", "Landslide", "Cold Wave", "Heat Wave", "Lightning", "Other")
    val ONFIELD_CONDITIONS = listOf("Excellent", "Good", "Fair", "Poor", "Complete Loss")
    val ACCOUNT_TYPES = listOf("Saving", "Current", "KCC (Loan Account)")
    val CROP_HEALTH = listOf("Excellent", "Good", "Fair", "Poor")
    val SOWING_METHODS = listOf("Manual", "Mechanised", "Broadcast", "Transplanted", "Drill Sowing")
    val CCE_PLOT_SIZES = listOf(
        "10 × 10 (100 sqm)", "5 × 5 (25 sqm)", "5 × 10 (50 sqm)"
    )
    val CCE_PLOT_SHAPES = listOf("Triangle", "Rectangle", "Square", "Circle")
    val CCE_THRESHING_METHODS = listOf("Manual Threshing", "Mechanical Thresher", "Pedal Thresher")
    val CCE_CROP_CONDITION    = listOf("Normal", "Lodging", "Partial Damage", "Heavy Damage", "Complete Failure")
    val CCE_WITNESS_TYPES     = listOf(
        "IC Representative", "Revenue Officer (Patwari)", "Agriculture Officer",
        "Farmer", "Farmer + IC Representative", "Other"
    )
    val CCE_SAMPLING_METHOD   = listOf("Random Sampling", "Systematic Sampling", "Smart Sampling (SST)")
}

data class PhotoRequirement(
    val key: String,
    val label: String,
    val instruction: String?,
    val required: Boolean
)