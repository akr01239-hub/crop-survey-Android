package com.cropsurvey.app.i18n

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * LanguageManager
 *
 * Central singleton for all language selection, persistence, and locale switching.
 * Works fully offline — language preference stored in SharedPreferences.
 *
 * ARCHITECTURE:
 *  - Language selected BEFORE login (LanguageSelectionActivity → LoginActivity)
 *  - Persists across logout and app restarts
 *  - Can be changed anytime from Settings → Language (no restart required)
 *  - Dashboard/Web panels NOT affected — they stay English
 *
 * DATABASE RULE:
 *  - Dropdown values stored as CODES (e.g. "MAIZE") never translated labels
 *  - UI displays translated labels; database/API always receives codes
 */
object LanguageManager {

    private const val PREF_NAME = "crop_survey_language"
    private const val KEY_LANGUAGE = "selected_language"
    private const val DEFAULT_LANGUAGE = "en"

    private lateinit var prefs: SharedPreferences

    /**
     * All supported languages.
     * To add a new language: add entry here + create res/values-XX/strings.xml
     */
    val SUPPORTED_LANGUAGES = listOf(
        Language("en",  "English",   "English",    isRtl = false),
        Language("hi",  "Hindi",     "हिन्दी",     isRtl = false),
        Language("mr",  "Marathi",   "मराठी",      isRtl = false),
        Language("bn",  "Bengali",   "বাংলা",      isRtl = false),
        Language("ta",  "Tamil",     "தமிழ்",      isRtl = false),
        Language("te",  "Telugu",    "తెలుగు",     isRtl = false),
        Language("kn",  "Kannada",   "ಕನ್ನಡ",      isRtl = false),
        Language("ur",  "Urdu",      "اردو",       isRtl = true),
        Language("or",  "Odia",      "ଓଡ଼ିଆ",      isRtl = false),
        Language("gu",  "Gujarati",  "ગુજરાતી",    isRtl = false),
        Language("pa",  "Punjabi",   "ਪੰਜਾਬੀ",     isRtl = false),
        Language("as",  "Assamese",  "অসমীয়া",    isRtl = false),
        Language("sat", "Santali",   "ᱥᱟᱱᱛᱟᱲᱤ",   isRtl = false),
        Language("ml",  "Malayalam", "മലയാളം",     isRtl = false),
        Language("ks",  "Kashmiri",  "کٲشُر",      isRtl = true),
        Language("brx", "Bodo",      "बड़ो",        isRtl = false)
    )

    fun init(context: Context) {
        prefs = context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getSelectedLanguageCode(): String =
        prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE

    fun setLanguage(context: Context, languageCode: String) {
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply()
        applyLocale(context, languageCode)
    }

    fun isRtl(): Boolean =
        SUPPORTED_LANGUAGES.find { it.code == getSelectedLanguageCode() }?.isRtl ?: false

    /**
     * Wraps the base context with the correct locale.
     * Call from every Activity.attachBaseContext().
     */
    fun applyLocale(base: Context, langCode: String? = null): Context {
        val code = langCode ?: getSelectedLanguageCode()
        val locale = buildLocale(code)
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)

        // RTL layout direction
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLayoutDirection(locale)
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            base.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            base.resources.updateConfiguration(config, base.resources.displayMetrics)
            base
        }
    }

    private fun buildLocale(code: String): Locale = when (code) {
        "or"  -> Locale("or",  "IN")
        "ur"  -> Locale("ur",  "IN")
        "ks"  -> Locale("ks",  "IN")
        "sat" -> Locale("sat", "IN")
        "brx" -> Locale("brx", "IN")
        "as"  -> Locale("as",  "IN")
        "ml"  -> Locale("ml",  "IN")
        else  -> Locale(code,  "IN")
    }

    /** Returns translated display name for current UI locale */
    fun getLanguageDisplayName(code: String): String =
        SUPPORTED_LANGUAGES.find { it.code == code }?.nativeName ?: code

    data class Language(
        val code: String,
        val englishName: String,
        val nativeName: String,
        val isRtl: Boolean
    )
}