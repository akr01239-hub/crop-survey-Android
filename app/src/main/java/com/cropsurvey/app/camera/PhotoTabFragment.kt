package com.cropsurvey.app.camera

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cropsurvey.app.R
import com.cropsurvey.app.config.AppConfig
import com.cropsurvey.app.config.PhotoRequirement
import com.cropsurvey.app.survey.SurveyTabsActivity
import com.cropsurvey.app.utils.SurveySession

/**
 * Fragment shown in the "Photos" tab of SurveyTabsActivity.
 *
 * Mirrors the behaviour of the old PhotoSelectionActivity but lives inside
 * a ViewPager2 so the user can freely switch between the Form and Photos tabs.
 *
 * All captured photos survive tab switches because they are stored in
 * SurveySession.capturedPhotos (an in-memory map) which is also persisted to
 * SharedPreferences on every save-draft call.
 */
class PhotoTabFragment : Fragment() {

    companion object {
        fun newInstance(surveyType: String, surveyId: String, isResubmit: Boolean): PhotoTabFragment {
            return PhotoTabFragment().apply {
                arguments = Bundle().apply {
                    putString("survey_type",  surveyType)
                    putString("survey_id",    surveyId)
                    putBoolean("is_resubmit", isResubmit)
                }
            }
        }
    }

    private lateinit var surveyType:  String
    private lateinit var surveyId:    String
    private var isResubmit = false

    private lateinit var rvPhotos:    RecyclerView
    private lateinit var tvSubtitle:  TextView
    private lateinit var adapter:     PhotoListAdapter

    private lateinit var requirements: List<PhotoRequirement>

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        adapter.notifyDataSetChanged()
        refreshSubtitle()
        // Notify parent activity to refresh the tab counter
        (activity as? SurveyTabsActivity)?.refreshPhotoCount()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        surveyType   = arguments?.getString("survey_type")   ?: "CLS"
        surveyId     = arguments?.getString("survey_id")     ?: ""
        isResubmit   = arguments?.getBoolean("is_resubmit")  ?: false
        requirements = AppConfig.getPhotosForType(requireContext(), surveyType)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_photo_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvPhotos   = view.findViewById(R.id.rv_photo_tab)
        tvSubtitle = view.findViewById(R.id.tv_photo_tab_subtitle)

        adapter = PhotoListAdapter(requirements) { index ->
            val intent = Intent(requireContext(), PhotoCaptureActivity::class.java)
            intent.putExtra("survey_type",  surveyType)
            intent.putExtra("survey_id",    surveyId)
            intent.putExtra("photo_index",  index)
            captureLauncher.launch(intent)
        }
        rvPhotos.layoutManager = LinearLayoutManager(requireContext())
        rvPhotos.adapter = adapter

        // Restore locally persisted photos (for drafts that survived process death)
        if (!isResubmit) {
            SurveySession.restorePhotosDraft(surveyId)
            adapter.notifyDataSetChanged()
        }
        refreshSubtitle()
    }

    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged()
        refreshSubtitle()
    }

    private fun refreshSubtitle() {
        val typeName = when (surveyType) {
            "CLS" -> "Crop Loss Survey"
            "CHM" -> "Crop Health Monitoring"
            "CCE" -> "Crop Cutting Experiment"
            else  -> "Survey"
        }
        val captured = requirements.count { it.key in SurveySession.capturedPhotos.keys }
        tvSubtitle.text = "$typeName — $captured / ${requirements.size} captured"
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
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = layoutInflater.inflate(R.layout.item_photo_slot, parent, false)
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

            holder.btnCapture.setOnClickListener {
                val currentPos = holder.bindingAdapterPosition
                if (currentPos != RecyclerView.NO_ID.toInt()) onCapture(currentPos)
            }
        }

        override fun getItemCount() = items.size
    }
}