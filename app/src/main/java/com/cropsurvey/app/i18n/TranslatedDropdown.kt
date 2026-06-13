package com.cropsurvey.app.i18n

import android.content.Context
import com.cropsurvey.app.R

/**
 * TranslatedDropdown — codes match AppConfig/DB exactly.
 * Helper functions are TOP-LEVEL so they're callable from any fragment.
 */
object TranslatedDropdown {

    data class Option(val code: String, val label: String)

    fun seasons(ctx: Context): List<Option> = listOf(
        Option("Kharif", ctx.getString(R.string.season_kharif)),
        Option("Rabi",   ctx.getString(R.string.season_rabi)),
        Option("Zaid",   ctx.getString(R.string.season_zaid))
    )

    fun schemes(ctx: Context): List<Option> = listOf(
        Option("PMFBY",  "PMFBY"),
        Option("Others", ctx.getString(R.string.crop_other))
    )

    fun crops(ctx: Context): List<Option> = listOf(
        Option("Wheat",       ctx.getString(R.string.crop_wheat)),
        Option("Rice",        ctx.getString(R.string.crop_rice)),
        Option("Mango",       ctx.getString(R.string.crop_mango)),
        Option("Soybean",     ctx.getString(R.string.crop_soybean)),
        Option("Cotton",      ctx.getString(R.string.crop_cotton)),
        Option("Sugarcane",   ctx.getString(R.string.crop_sugarcane)),
        Option("Banana",      ctx.getString(R.string.crop_banana)),
        Option("Tomato",      ctx.getString(R.string.crop_tomato)),
        Option("Potato",      ctx.getString(R.string.crop_potato)),
        Option("Onion",       ctx.getString(R.string.crop_onion)),
        Option("Sorghum",     ctx.getString(R.string.crop_sorghum)),
        Option("Maize",       ctx.getString(R.string.crop_maize)),
        Option("Groundnut",   ctx.getString(R.string.crop_groundnut)),
        Option("Sunflower",   ctx.getString(R.string.crop_sunflower)),
        Option("Gram",        ctx.getString(R.string.crop_gram)),
        Option("Tur (Arhar)", ctx.getString(R.string.crop_tur)),
        Option("Moong",       ctx.getString(R.string.crop_moong)),
        Option("Urad",        ctx.getString(R.string.crop_urad)),
        Option("Linseed",     ctx.getString(R.string.crop_linseed)),
        Option("Mustard",     ctx.getString(R.string.crop_mustard)),
        Option("Grapes",      ctx.getString(R.string.crop_grapes)),
        Option("Pomegranate", ctx.getString(R.string.crop_pomegranate)),
        Option("Orange",      ctx.getString(R.string.crop_orange)),
        Option("Other",       ctx.getString(R.string.crop_other))
    )

    fun cropStages(ctx: Context): List<Option> = listOf(
        Option("Sowing",        ctx.getString(R.string.stage_sowing)),
        Option("Germination",   ctx.getString(R.string.stage_germination)),
        Option("Vegetative",    ctx.getString(R.string.stage_vegetative)),
        Option("Tillering",     ctx.getString(R.string.stage_tillering)),
        Option("Flowering",     ctx.getString(R.string.stage_flowering)),
        Option("Grain Filling", ctx.getString(R.string.stage_grain_filling)),
        Option("Maturity",      ctx.getString(R.string.stage_maturity)),
        Option("Harvesting",    ctx.getString(R.string.stage_harvesting))
    )

    fun irrigationTypes(ctx: Context): List<Option> = listOf(
        Option("Irrigated",           ctx.getString(R.string.irrigation_irrigated)),
        Option("Rainfed",             ctx.getString(R.string.irrigation_rainfed)),
        Option("Partially Irrigated", ctx.getString(R.string.irrigation_partial))
    )

    fun landTypes(ctx: Context): List<Option> = listOf(
        Option("Plain",      ctx.getString(R.string.land_plain)),
        Option("Hilly",      ctx.getString(R.string.land_hilly)),
        Option("Undulating", ctx.getString(R.string.land_undulating)),
        Option("Coastal",    ctx.getString(R.string.land_coastal)),
        Option("Wetland",    ctx.getString(R.string.land_wetland))
    )

    fun causeOfEvent(ctx: Context): List<Option> = listOf(
        Option("Heavy Rainfall", ctx.getString(R.string.cause_heavy_rain)),
        Option("Drought",        ctx.getString(R.string.cause_drought)),
        Option("Hailstorm",      ctx.getString(R.string.cause_hailstorm)),
        Option("Flood",          ctx.getString(R.string.cause_flood)),
        Option("Cyclone",        ctx.getString(R.string.cause_cyclone)),
        Option("Pest & Disease", ctx.getString(R.string.cause_pest)),
        Option("Fire",           ctx.getString(R.string.cause_fire)),
        Option("Landslide",      ctx.getString(R.string.cause_landslide)),
        Option("Cold Wave",      ctx.getString(R.string.cause_cold_wave)),
        Option("Heat Wave",      ctx.getString(R.string.cause_heat_wave)),
        Option("Lightning",      ctx.getString(R.string.cause_lightning)),
        Option("Other",          ctx.getString(R.string.cause_other))
    )

    fun onfieldConditions(ctx: Context): List<Option> = listOf(
        Option("Excellent",     ctx.getString(R.string.condition_excellent)),
        Option("Good",          ctx.getString(R.string.condition_good)),
        Option("Fair",          ctx.getString(R.string.condition_fair)),
        Option("Poor",          ctx.getString(R.string.condition_poor)),
        Option("Complete Loss", ctx.getString(R.string.condition_complete_loss))
    )

    fun accountTypes(ctx: Context): List<Option> = listOf(
        Option("Saving",             ctx.getString(R.string.account_saving)),
        Option("Current",            ctx.getString(R.string.account_current)),
        Option("KCC (Loan Account)", ctx.getString(R.string.account_kcc))
    )

    fun croppingPatterns(ctx: Context): List<Option> = listOf(
        Option("Single",    ctx.getString(R.string.pattern_single)),
        Option("Mixed",     ctx.getString(R.string.pattern_mixed)),
        Option("Intercrop", ctx.getString(R.string.pattern_intercrop))
    )

    fun insuranceUnits(ctx: Context): List<Option> = listOf(
        Option("RICircle Level", ctx.getString(R.string.unit_ri_circle)),
        Option("Village Level",  ctx.getString(R.string.unit_village)),
        Option("District Level", ctx.getString(R.string.unit_district)),
        Option("Block Level",    ctx.getString(R.string.unit_block)),
        Option("GP/NP",          ctx.getString(R.string.unit_gp_np))
    )

    fun cropHealth(ctx: Context): List<Option> = listOf(
        Option("Excellent", ctx.getString(R.string.condition_excellent)),
        Option("Good",      ctx.getString(R.string.condition_good)),
        Option("Fair",      ctx.getString(R.string.condition_fair)),
        Option("Poor",      ctx.getString(R.string.condition_poor))
    )

    fun sowingMethods(ctx: Context): List<Option> = listOf(
        Option("Manual",       "Manual"),
        Option("Mechanised",   "Mechanised"),
        Option("Broadcast",    "Broadcast"),
        Option("Transplanted", "Transplanted"),
        Option("Drill Sowing", "Drill Sowing")
    )

    fun yesNo(ctx: Context): List<Option> = listOf(
        Option("yes", ctx.getString(R.string.yes)),
        Option("no",  ctx.getString(R.string.no))
    )

    fun cropSituationField(ctx: Context): List<Option> = listOf(
        Option("cut_spread",    ctx.getString(R.string.crop_sit_cut_spread)),
        Option("bundle",        ctx.getString(R.string.crop_sit_bundle)),
        Option("field_removed", ctx.getString(R.string.crop_sit_field_removed))
    )
}

// ── Top-level helper functions — callable directly from any fragment ───────────

/** Get labels list for ArrayAdapter */
fun tdLabels(opts: List<TranslatedDropdown.Option>): List<String> = opts.map { it.label }

/** Get code at 0-based position (position 0 in the options list = spinner position 1 due to placeholder) */
fun tdCode(opts: List<TranslatedDropdown.Option>, position: Int): String? =
    opts.getOrNull(position)?.code

/** Find 0-based index of option by code, -1 if not found */
fun tdPosition(opts: List<TranslatedDropdown.Option>, code: String?): Int =
    opts.indexOfFirst { it.code == code }

/** Set spinner to the position matching the given code (+1 for placeholder at pos 0) */
fun tdRestore(spinner: android.widget.Spinner, opts: List<TranslatedDropdown.Option>, code: String?) {
    if (code == null) return
    val pos = tdPosition(opts, code)
    if (pos >= 0) spinner.setSelection(pos + 1, false)
}