package com.cropsurvey.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cropsurvey.app.i18n.LanguageManager

open class BaseActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_LANGUAGE_CHANGE = 9901
    }

    // Track the language code that was active when this activity was created
    private var activityLanguageCode: String = ""

    override fun attachBaseContext(newBase: Context) {
        val localizedContext = LanguageManager.applyLocale(newBase)
        super.attachBaseContext(localizedContext)
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        activityLanguageCode = LanguageManager.getSelectedLanguageCode()
    }

    override fun onResume() {
        super.onResume()
        (application as? CropSurveyApp)?.setCurrentActivity(this)
        // If language changed while this activity was in the back stack, recreate it
        val currentCode = LanguageManager.getSelectedLanguageCode()
        if (activityLanguageCode.isNotEmpty() && activityLanguageCode != currentCode) {
            activityLanguageCode = currentCode
            recreate()
        }
    }

    override fun onPause() {
        super.onPause()
        (application as? CropSurveyApp)?.setCurrentActivity(null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_LANGUAGE_CHANGE && resultCode == RESULT_OK) {
            recreate()
        }
    }
}
