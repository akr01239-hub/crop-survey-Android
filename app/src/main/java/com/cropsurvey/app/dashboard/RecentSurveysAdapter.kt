package com.cropsurvey.app.dashboard

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.cropsurvey.app.R
import com.cropsurvey.app.models.Survey

class RecentSurveysAdapter(
    private var surveys: List<Survey>,
    private val onEditDraft: (Survey) -> Unit,
    private val onSubmitDraft: (Survey) -> Unit,
    private val onDeleteDraft: (Survey) -> Unit,
    private val onEditRejected: (Survey) -> Unit,
    private val onClick: (Survey) -> Unit
) : RecyclerView.Adapter<RecentSurveysAdapter.ViewHolder>() {

    /** Swap the list and animate changes via DiffUtil */
    fun updateSurveys(newList: List<Survey>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = surveys.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                surveys[oldPos].id == newList[newPos].id
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                surveys[oldPos] == newList[newPos]
        })
        surveys = newList
        diff.dispatchUpdatesTo(this)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCaseId: TextView          = view.findViewById(R.id.tv_case_id)
        val tvFarmer: TextView          = view.findViewById(R.id.tv_farmer)
        val tvSubtitle: TextView        = view.findViewById(R.id.tv_subtitle)
        val tvType: TextView            = view.findViewById(R.id.tv_type)
        val tvStatus: TextView          = view.findViewById(R.id.tv_status)
        val tvLocation: TextView        = view.findViewById(R.id.tv_location)
        val tvRejectionReason: TextView = view.findViewById(R.id.tv_rejection_reason)
        val layoutRejection: View       = view.findViewById(R.id.layout_rejection_reason)
        val layoutActions: View         = view.findViewById(R.id.layout_action_buttons)
        val btnEditDraft: Button        = view.findViewById(R.id.btn_edit_draft)
        val btnSubmitDraft: Button      = view.findViewById(R.id.btn_submit_draft)
        val btnDeleteDraft: Button      = view.findViewById(R.id.btn_delete_draft)
        val btnEditRejected: Button     = view.findViewById(R.id.btn_edit_rejected)
        val viewAccent: View            = view.findViewById(R.id.view_accent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_survey, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val survey = surveys[position]
        val fd = survey.formData

        // ── Case ID: for CHM show chm_case_id-visitNumber, others show survey caseId
        val displayCaseId = if (survey.surveyType == "CHM") {
            val chmCaseId   = fd["chm_case_id"]?.toString()
            val visitNumber = (fd["chm_visit_number"] as? Double)?.toInt()
                ?: fd["chm_visit_number"]?.toString()?.toIntOrNull()
            when {
                chmCaseId != null && visitNumber != null -> "$chmCaseId-$visitNumber"
                chmCaseId != null -> chmCaseId
                else -> survey.caseId
            }
        } else {
            survey.caseId
        }
        holder.tvCaseId.text = displayCaseId

        holder.tvFarmer.text   = fd["farmer_name"]?.toString() ?: "—"

        // ── Subtitle: CCE shows crop · CCE number · date; others hidden
        if (survey.surveyType == "CCE") {
            val crop      = fd["crop_name"]?.toString()?.takeIf { it.isNotBlank() }
            val cceNum    = fd["cce_number"]?.toString()?.takeIf { it.isNotBlank() }
            val cceDate   = fd["cce_date"]?.toString()?.takeIf { it.isNotBlank() }
            val parts     = listOfNotNull(crop, cceNum, cceDate)
            if (parts.isNotEmpty()) {
                holder.tvSubtitle.text       = parts.joinToString("  ·  ")
                holder.tvSubtitle.visibility = View.VISIBLE
            } else {
                holder.tvSubtitle.visibility = View.GONE
            }
        } else {
            holder.tvSubtitle.visibility = View.GONE
        }

        holder.tvType.text     = survey.surveyType

        // ── Location: show state·district if available, else GPS coords, else "—"
        val state    = fd["state"]?.toString()
        val district = fd["district"]?.toString()
        val kmlLat   = (fd["kml_lat"] as? Double) ?: (fd["capture_lat"] as? Double)
        val kmlLon   = (fd["kml_lon"] as? Double) ?: (fd["capture_lon"] as? Double)

        holder.tvLocation.text = when {
            !state.isNullOrBlank() -> listOfNotNull(state, district?.takeIf { it.isNotBlank() })
                .joinToString(" · ")
            kmlLat != null && kmlLon != null ->
                "%.5f°N  %.5f°E".format(kmlLat, kmlLon)
            else -> "Location not set"
        }

        // Status label + color
        holder.tvStatus.text = when (survey.status) {
            "submitted_pending" -> "⏳ Submitted (Syncing)"
            "draft"             -> "Draft"
            "submitted"         -> "Submitted"
            "under_qc"          -> "Under QC"
            "approved"          -> "✓ Approved"
            "rejected"          -> "✗ Rejected"
            else                -> survey.status.replace("_", " ").replaceFirstChar { it.uppercase() }
        }

        val (statusBg, statusText) = when (survey.status) {
            "approved"          -> "#F0FDF4" to "#16A34A"
            "rejected"          -> "#FEF2F2" to "#DC2626"
            "submitted"         -> "#EFF6FF" to "#2563EB"
            "submitted_pending" -> "#FFF7ED" to "#D97706"
            "under_qc"          -> "#FFFBEB" to "#D97706"
            else                -> "#F1F5F9" to "#64748B"
        }
        holder.tvStatus.setBackgroundColor(Color.parseColor(statusBg))
        holder.tvStatus.setTextColor(Color.parseColor(statusText))

        // Left accent stripe color
        val accentColor = when (survey.status) {
            "approved"          -> "#16A34A"
            "rejected"          -> "#DC2626"
            "submitted"         -> "#2563EB"
            "submitted_pending" -> "#D97706"
            "under_qc"          -> "#D97706"
            "draft"             -> "#6366F1"
            else                -> "#94A3B8"
        }
        holder.viewAccent.setBackgroundColor(Color.parseColor(accentColor))

        // Rejection reason row
        val isRejected = survey.status == "rejected"
        if (isRejected && !survey.rejectionReason.isNullOrBlank()) {
            holder.layoutRejection.visibility = View.VISIBLE
            holder.tvRejectionReason.text = survey.rejectionReason
        } else {
            holder.layoutRejection.visibility = View.GONE
        }

        // Action buttons
        val isDraft = survey.status == "draft"
        if (isDraft || isRejected) {
            holder.layoutActions.visibility = View.VISIBLE
        } else {
            holder.layoutActions.visibility = View.GONE
        }

        // Draft: show Edit + Submit + Delete buttons
        if (isDraft) {
            holder.btnEditDraft.visibility    = View.VISIBLE
            holder.btnSubmitDraft.visibility  = View.VISIBLE
            holder.btnDeleteDraft.visibility  = View.VISIBLE
            holder.btnEditRejected.visibility = View.GONE

            holder.btnEditDraft.setOnClickListener   { onEditDraft(survey) }
            holder.btnSubmitDraft.setOnClickListener { onSubmitDraft(survey) }
            holder.btnDeleteDraft.setOnClickListener { onDeleteDraft(survey) }
        }

        // Rejected: show Re-edit & Resubmit button
        if (isRejected) {
            holder.btnEditDraft.visibility    = View.GONE
            holder.btnSubmitDraft.visibility  = View.GONE
            holder.btnDeleteDraft.visibility  = View.GONE
            holder.btnEditRejected.visibility = View.VISIBLE

            holder.btnEditRejected.setOnClickListener { onEditRejected(survey) }
        }

        holder.itemView.setOnClickListener { onClick(survey) }
    }

    override fun getItemCount() = surveys.size
}
