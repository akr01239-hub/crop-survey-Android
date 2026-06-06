package com.cropsurvey.app.camera

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.cropsurvey.app.BaseActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cropsurvey.app.R
import com.cropsurvey.app.config.AppConfig
import com.cropsurvey.app.config.PhotoRequirement
import com.cropsurvey.app.models.CapturedPhoto
import com.cropsurvey.app.models.UpdateSurveyRequest
import com.cropsurvey.app.network.ApiClient
import com.cropsurvey.app.queue.QueueManager
import com.cropsurvey.app.survey.SubmitSurveyActivity
import com.cropsurvey.app.utils.SurveySession
import kotlinx.coroutines.launch

/**
 * Shows a scrollable list of all required photos for this survey type.
 *
 * When opened in resubmit mode (rejected survey), existing server photos are loaded
 * first so their slots show a tick and "Retake" label. The user only retakes photos
 * they want to replace — all others are kept as-is on the server.
 */
class PhotoSelectionActivity : BaseActivity() {

    private lateinit var surveyType: String
    private lateinit var surveyId: String
    private var isResubmit = false
    private var chmVisitNumber: Int = 0
    private var chmCaseId: String? = null

    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var rvPhotos: RecyclerView
    private lateinit var btnProceed: Button
    private lateinit var btnSaveDraft: Button
    private lateinit var btnBack: ImageButton

    private lateinit var requirements: List<PhotoRequirement>
    private lateinit var adapter: PhotoListAdapter

    private var captureIndex = -1

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        adapter.notifyDataSetChanged()
        refreshProceedButton()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_selection)

        surveyType   = intent.getStringExtra("survey_type") ?: "CLS"
        surveyId     = intent.getStringExtra("survey_id")   ?: ""
        isResubmit   = intent.getBooleanExtra("is_resubmit", false)
        requirements = AppConfig.getPhotosForType(this, surveyType)
        chmVisitNumber = intent.getIntExtra("chm_visit_number", 0)
        chmCaseId      = intent.getStringExtra("chm_case_id")

        bindViews()
        setupHeader()
        setupRecyclerView()
        refreshProceedButton()

        // Restore locally cached photos for draft mode (photos survive app restart)
        if (!isResubmit) {
            SurveySession.restorePhotosDraft(surveyId)
            adapter.notifyDataSetChanged()
            refreshProceedButton()
        }

        // Load existing server photos so tick marks appear on already-uploaded slots
        if (isResubmit) {
            loadExistingPhotos()
        }
    }

    private fun bindViews() {
        tvTitle      = findViewById(R.id.tv_photo_selection_title)
        tvSubtitle   = findViewById(R.id.tv_photo_selection_subtitle)
        rvPhotos     = findViewById(R.id.rv_photos)
        btnProceed   = findViewById(R.id.btn_proceed_submit)
        btnSaveDraft = findViewById(R.id.btn_save_draft)
        btnBack      = findViewById(R.id.btn_back)
        // NOTE: no progress_bar in activity_photo_selection.xml — loading is shown via Toast only
    }

    private fun setupHeader() {
        val typeName = when (surveyType) {
            "CLS" -> "Crop Loss Survey"
            "CHM" -> "Crop Health Monitoring"
            "CCE" -> "Crop Cutting Experiment"
            else  -> "Survey"
        }
        tvTitle.text    = "Photo Capture"
        tvSubtitle.text = "$typeName — ${requirements.size} photos required"

        btnBack.setOnClickListener {
            val capturedCount = SurveySession.capturedPhotos.size
            if (capturedCount > 0) {
                AlertDialog.Builder(this)
                    .setTitle("Leave Photo Capture?")
                    .setMessage("You have captured $capturedCount photo(s). Save as draft so you can continue later?")
                    .setPositiveButton("Save Draft") { _, _ -> saveDraft { finish() } }
                    .setNegativeButton("Leave Without Saving") { _, _ -> finish() }
                    .setNeutralButton("Cancel", null)
                    .show()
            } else {
                finish()
            }
        }

        btnSaveDraft.setOnClickListener {
            saveDraft {
                Toast.makeText(this, "Draft saved — you can continue later from the dashboard.", Toast.LENGTH_LONG).show()
                finish()
            }
        }

        btnProceed.setOnClickListener {
            val capturedKeys = SurveySession.capturedPhotos.keys
            val missing      = requirements.filter { it.required && it.key !in capturedKeys }
            if (missing.isNotEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("Missing Photos")
                    .setMessage("Please capture: ${missing.joinToString(", ") { it.label }}")
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }
            val intent = Intent(this, SubmitSurveyActivity::class.java)
            intent.putExtra("survey_type", surveyType)
            intent.putExtra("survey_id", surveyId)
            if (chmVisitNumber > 0) {
                intent.putExtra("chm_visit_number", chmVisitNumber)
                chmCaseId?.let { intent.putExtra("chm_case_id", it) }
            }
            startActivity(intent)
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = PhotoListAdapter(requirements) { index ->
            captureIndex = index
            val intent = Intent(this, PhotoCaptureActivity::class.java)
            intent.putExtra("survey_type", surveyType)
            intent.putExtra("survey_id",   surveyId)
            intent.putExtra("photo_index", index)
            captureLauncher.launch(intent)
        }
        rvPhotos.layoutManager = LinearLayoutManager(this)
        rvPhotos.adapter       = adapter
    }

    private fun refreshProceedButton() {
        val capturedKeys = SurveySession.capturedPhotos.keys
        val allDone      = requirements.filter { it.required }.all { it.key in capturedKeys }
        btnProceed.isEnabled = allDone
        btnProceed.alpha     = if (allDone) 1f else 0.5f
    }

    /**
     * Fetches photos already stored on the server for this survey and registers
     * them in SurveySession.capturedPhotos so the adapter shows tick marks and
     * "Retake" labels. Only fills slots not already retaken locally this session.
     */
    private fun loadExistingPhotos() {
        btnProceed.isEnabled = false
        tvSubtitle.text = "${tvSubtitle.text} — loading existing photos…"

        lifecycleScope.launch {
            try {
                val res = ApiClient.service.getPhotos(surveyId)
                if (res.isSuccessful) {
                    val serverPhotos = res.body() ?: emptyList()
                    for (sp in serverPhotos) {
                        if (!SurveySession.capturedPhotos.containsKey(sp.photoKey)) {
                            SurveySession.capturedPhotos[sp.photoKey] = CapturedPhoto(
                                photoKey = sp.photoKey,
                                localUri = sp.signedUrl ?: sp.storageUrl,
                                lat      = sp.lat ?: 0.0,
                                lon      = sp.lon ?: 0.0,
                                accuracy = null,
                                uploaded = true
                            )
                        }
                    }
                } else {
                    Toast.makeText(this@PhotoSelectionActivity,
                        "Could not load existing photos — retake any missing ones.",
                        Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@PhotoSelectionActivity,
                    "Offline — could not load existing photos. Retake any that are missing.",
                    Toast.LENGTH_LONG).show()
            } finally {
                // Reset subtitle and refresh UI
                val typeName = when (surveyType) {
                    "CLS" -> "Crop Loss Survey"
                    "CHM" -> "Crop Health Monitoring"
                    "CCE" -> "Crop Cutting Experiment"
                    else  -> "Survey"
                }
                tvSubtitle.text = "$typeName — ${requirements.size} photos required"
                adapter.notifyDataSetChanged()
                refreshProceedButton()
            }
        }
    }

    private fun saveDraft(onDone: (() -> Unit)? = null) {
        // Always persist photo paths locally — even if API call fails the user
        // won't lose their captured photos when they re-open the draft.
        SurveySession.savePhotosDraft()

        btnSaveDraft.isEnabled = false
        lifecycleScope.launch {
            try {
                val res = ApiClient.service.updateSurvey(
                    surveyId,
                    UpdateSurveyRequest(SurveySession.formData)
                )
                if (res.isSuccessful) {
                    onDone?.invoke()
                } else {
                    QueueManager.enqueueFormUpdate(this@PhotoSelectionActivity, surveyId, SurveySession.formData)
                    onDone?.invoke()
                }
            } catch (e: Exception) {
                QueueManager.enqueueFormUpdate(this@PhotoSelectionActivity, surveyId, SurveySession.formData)
                onDone?.invoke()
            } finally {
                btnSaveDraft.isEnabled = true
            }
        }
    }

    // ── Adapter ───────────────────────────────────────────────────────────────
    inner class PhotoListAdapter(
        private val items: List<PhotoRequirement>,
        private val onCapture: (Int) -> Unit
    ) : RecyclerView.Adapter<PhotoListAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvIndex:       TextView  = view.findViewById(R.id.tv_photo_index)
            val tvLabel:       TextView  = view.findViewById(R.id.tv_photo_item_label)
            val tvInstruction: TextView  = view.findViewById(R.id.tv_photo_item_instruction)
            val btnCapture:    Button    = view.findViewById(R.id.btn_capture_photo)
            val ivTick:        ImageView = view.findViewById(R.id.iv_tick)
            val cardRoot:      View      = view.findViewById(R.id.card_photo_item)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_photo_slot, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val req        = items[position]
            val isCaptured = req.key in SurveySession.capturedPhotos.keys

            holder.tvIndex.text = "${position + 1}"
            holder.tvLabel.text = req.label

            if (req.instruction != null) {
                holder.tvInstruction.visibility = View.VISIBLE
                holder.tvInstruction.text       = req.instruction
            } else {
                holder.tvInstruction.visibility = View.GONE
            }

            holder.ivTick.visibility = if (isCaptured) View.VISIBLE else View.GONE
            holder.btnCapture.text   = if (isCaptured) "Retake" else "Capture"
            holder.cardRoot.alpha    = if (isCaptured) 0.85f else 1f

            holder.btnCapture.setOnClickListener {
                // FIX BUG 3: Do NOT capture `position` from onBindViewHolder — RecyclerView
                // recycles ViewHolders so the closure goes stale after notifyDataSetChanged().
                // E.g. retaking Photo 1 would fire onCapture(13) because that VH was last bound
                // at position 13 before the dataset refresh. Use bindingAdapterPosition instead,
                // which always reflects the VH's current row at the moment of the tap.
                val currentPos = holder.bindingAdapterPosition
                if (currentPos != RecyclerView.NO_ID.toInt()) onCapture(currentPos)
            }
        }

        override fun getItemCount() = items.size
    }
}