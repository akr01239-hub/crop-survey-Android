package com.cropsurvey.app.i18n

import android.content.Context
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import org.json.JSONObject

/**
 * FreeTextFieldHelper
 *
 * Handles free-text fields where users type in their own regional language.
 * Supports: Remarks, Observations, Comments, Damage Description, Farmer Statement,
 *           Visit Notes, Recommendations.
 *
 * DATABASE STORAGE FORMAT:
 * {
 *   "remarks_original": "पिकाची वाढ चांगली आहे",
 *   "remarks_language": "mr",
 *   "remarks_english": null    ← filled by optional translation service
 * }
 *
 * NEVER translate field names/identifiers (Farmer Name, Survey No., Aadhaar, Bank Account, GPS).
 */
object FreeTextFieldHelper {

    // ── Fields that MUST NOT be translated — store exactly as entered ──────────
    private val NO_TRANSLATE_FIELDS = setOf(
        "farmer_name",
        "survey_number",
        "mobile_number",
        "farmer_mobile",
        "aadhaar_number",
        "farmer_aadhaar_last4",
        "bank_account_no",
        "account_no",
        "gps_lat",
        "gps_lon",
        "plot_id",
        "land_record_number",
        "khasra_no",
        "ifsc_code",
        "farmer_app_no",
        "cce_number",
        "experiment_id",
        "survey_intimation_no"
    )

    // ── Fields that accept regional language input ─────────────────────────────
    val REGIONAL_TEXT_FIELDS = setOf(
        "remarks",
        "observations",
        "comments",
        "notes",
        "damage_description",
        "farmer_statement",
        "visit_notes",
        "recommendations",
        "officer_designation"
    )

    /**
     * Configure an EditText for regional language free-text input.
     * Enables full Unicode support for all Indian scripts.
     */
    fun configureForRegionalInput(editText: EditText, fieldKey: String) {
        if (fieldKey in REGIONAL_TEXT_FIELDS) {
            // Allow all Unicode characters (covers Devanagari, Bengali, Tamil, etc.)
            editText.inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            editText.imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION
        }
    }

    /**
     * Build the storage object for a free-text field.
     *
     * @param fieldKey    e.g. "remarks"
     * @param text        the user-typed text
     * @param langCode    current language code from LanguageManager
     * @return JSONObject  { original, language, english }
     */
    fun buildStorage(fieldKey: String, text: String, langCode: String): JSONObject {
        return JSONObject().apply {
            put("${fieldKey}_original", text)
            put("${fieldKey}_language", langCode)
            put("${fieldKey}_english", JSONObject.NULL)  // filled by backend translation service
        }
    }

    /**
     * Restore display text from stored JSON object.
     * Shows original text in the user's language.
     */
    fun restoreText(stored: JSONObject?, fieldKey: String): String {
        if (stored == null) return ""
        return stored.optString("${fieldKey}_original", "")
    }

    /**
     * Returns true if this field should NOT have its value translated/stored differently.
     * Use this to skip language processing for identifier fields.
     */
    fun isIdentifierField(fieldKey: String): Boolean = fieldKey in NO_TRANSLATE_FIELDS

    /**
     * Returns the input type for a given field.
     * Identifier fields (phone, aadhaar) get numeric/restricted input.
     * Regional fields get full text.
     */
    fun inputTypeFor(fieldKey: String): Int = when {
        fieldKey in setOf("farmer_mobile", "mobile_number", "officer_mobile") ->
            InputType.TYPE_CLASS_PHONE
        fieldKey in setOf("farmer_aadhaar_last4") ->
            InputType.TYPE_CLASS_NUMBER
        fieldKey in setOf("gps_lat", "gps_lon", "field_area_polygon",
            "total_land_area", "insured_area", "area_affected_pct",
            "loss_pct", "fresh_biomass", "fresh_grain", "dry_grain",
            "moisture_pct", "yield_kg_per_ha") ->
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        fieldKey in REGIONAL_TEXT_FIELDS ->
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        else ->
            InputType.TYPE_CLASS_TEXT
    }
}
