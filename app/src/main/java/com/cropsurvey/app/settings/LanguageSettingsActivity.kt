package com.cropsurvey.app.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cropsurvey.app.BaseActivity
import com.cropsurvey.app.R
import com.cropsurvey.app.auth.LoginActivity
import com.cropsurvey.app.dashboard.DashboardActivity
import com.cropsurvey.app.i18n.LanguageManager
import com.cropsurvey.app.utils.SessionManager

/**
 * LanguageSettingsActivity
 *
 * Accessible from Settings → Language.
 *
 * Requirements:
 *  - No logout required
 *  - No app restart required
 *  - Entire UI updates instantly when language is selected
 *  - Selected language saved permanently (SharedPreferences)
 *
 * Implementation:
 *  - On language change, recreate() is called which triggers attachBaseContext
 *    on ALL activities in the stack via FLAG_ACTIVITY_CLEAR_TOP on the DashboardActivity intent
 */
class LanguageSettingsActivity : BaseActivity() {

    private lateinit var rvLanguages: RecyclerView
    private var selectedCode = LanguageManager.getSelectedLanguageCode()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_language_settings)

        findViewById<View>(R.id.btn_back_language)?.setOnClickListener { finish() }
        rvLanguages = findViewById(R.id.rv_languages_settings)

        setupList()
    }

    private fun setupList() {
        val adapter = LanguageAdapter(
            languages    = LanguageManager.SUPPORTED_LANGUAGES,
            selectedCode = selectedCode,
            onSelect     = { code -> applyLanguage(code) }
        )
        rvLanguages.layoutManager = LinearLayoutManager(this)
        rvLanguages.adapter = adapter
    }

    private fun applyLanguage(code: String) {
        if (code == selectedCode) return

        // 1. Persist the new language
        LanguageManager.setLanguage(this, code)

        // 2. Show confirmation toast
        Toast.makeText(this, getString(R.string.language_changed), Toast.LENGTH_SHORT).show()

        // 3. Rebuild the entire activity stack with new locale
        //    FLAG_ACTIVITY_CLEAR_TASK kills all existing activities and recreates from root
        val intent = if (SessionManager.isLoggedIn()) {
            Intent(this, DashboardActivity::class.java)
        } else {
            Intent(this, LoginActivity::class.java)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // ─── Adapter ─────────────────────────────────────────────────────────────

    private inner class LanguageAdapter(
        private val languages: List<LanguageManager.Language>,
        private var selectedCode: String,
        private val onSelect: (String) -> Unit
    ) : RecyclerView.Adapter<LanguageAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvNative:  TextView = v.findViewById(R.id.tv_language_native)
            val tvEnglish: TextView = v.findViewById(R.id.tv_language_english)
            val ivCheck:   View     = v.findViewById(R.id.iv_language_check)
            val root:      View     = v
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_language, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val lang     = languages[position]
            val selected = lang.code == selectedCode

            holder.tvNative.text  = lang.nativeName
            holder.tvEnglish.text = lang.englishName
            holder.ivCheck.visibility = if (selected) View.VISIBLE else View.INVISIBLE
            holder.root.setBackgroundResource(
                if (selected) R.drawable.bg_language_selected else R.drawable.bg_language_normal
            )

            holder.root.setOnClickListener {
                val prev = languages.indexOfFirst { it.code == this.selectedCode }
                this.selectedCode = lang.code
                notifyItemChanged(prev)
                notifyItemChanged(position)
                onSelect(lang.code)
            }
        }

        override fun getItemCount() = languages.size
    }
}
