package com.capstone.navicamp

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.card.MaterialCardView
import android.view.animation.AnimationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class AssistanceModalDialog : DialogFragment() {

    private lateinit var respondButton: Button
    private lateinit var resolveButton: Button
    private lateinit var respondProgress: ProgressBar
    private lateinit var falseAlarmButton: Button
    private lateinit var officerStatusCard: MaterialCardView
    private lateinit var officerStatusText: TextView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var closeButton: Button

    // Post-resolve views
    private lateinit var postResolveButtons: LinearLayout
    private lateinit var makeReportButton: Button
    private lateinit var postResolveCloseButton: Button
    private lateinit var reportSection: LinearLayout
    private lateinit var reportLocationChips: FlexboxLayout
    private lateinit var reportOtherSection: LinearLayout
    private lateinit var reportActionFA: EditText
    private lateinit var reportActionInfo: EditText
    private lateinit var submitReportButton: Button
    private lateinit var actionButtonsContainer: LinearLayout

    private var locationID: String? = null
    private var alertID: String? = null
    private var hasOfficerResponded = false
    private var isResolved = false
    private var selectedReportLocation: String? = null

    private val reportLocationOptions = listOf("Gym", "Field", "Student Lounge", "Clinic", "Other")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_assistance_modal, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)

        val slideUpFadeIn = AnimationUtils.loadAnimation(requireContext(), R.anim.slide_up_fade_in)
        view.startAnimation(slideUpFadeIn)

        locationID = arguments?.getString("LOCATION_ID")
        alertID = arguments?.getString("ALERT_ID")

        populateViewData(view)
        showLoadingState()
        checkOfficerStatus(view)
        setupClickListeners()
    }

    private fun initializeViews(view: View) {
        respondButton = view.findViewById(R.id.respond_button)
        resolveButton = view.findViewById(R.id.resolve_button)
        respondProgress = view.findViewById(R.id.respond_progress)
        falseAlarmButton = view.findViewById(R.id.false_alarm_button)
        officerStatusCard = view.findViewById(R.id.officer_status_card)
        officerStatusText = view.findViewById(R.id.officer_status_text)
        loadingProgress = view.findViewById(R.id.respond_progress)
        closeButton = view.findViewById(R.id.close_button)

        postResolveButtons = view.findViewById(R.id.post_resolve_buttons)
        makeReportButton = view.findViewById(R.id.make_report_button)
        postResolveCloseButton = view.findViewById(R.id.post_resolve_close_button)
        reportSection = view.findViewById(R.id.report_section)
        reportLocationChips = view.findViewById(R.id.report_location_chips)
        reportOtherSection = view.findViewById(R.id.report_other_section)
        reportActionFA = view.findViewById(R.id.report_action_fa)
        reportActionInfo = view.findViewById(R.id.report_action_info)
        submitReportButton = view.findViewById(R.id.submit_report_button)

        actionButtonsContainer = respondButton.parent as LinearLayout
    }

    private fun populateViewData(view: View) {
        val floorLevel = arguments?.getString("FLOOR_LEVEL")
        val userID = arguments?.getString("USER_ID")
        val fullName = arguments?.getString("FULL_NAME")
        val dateTime = arguments?.getString("DATE_TIME")
        val status = arguments?.getString("STATUS")

        view.findViewById<TextView>(R.id.floor_level_text).text = floorLevel
        view.findViewById<TextView>(R.id.user_id_text).text = "ID: $userID"
        view.findViewById<TextView>(R.id.full_name_text).text = fullName
        view.findViewById<TextView>(R.id.date_time_text).text = formatDateTime(dateTime)
        view.findViewById<TextView>(R.id.status_badge).text = status?.uppercase()

        val statusBadge = view.findViewById<TextView>(R.id.status_badge)
        when (status?.lowercase()) {
            "pending" -> {
                statusBadge.background = ContextCompat.getDrawable(requireContext(), R.drawable.badge_pending)
                statusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange))
            }
            "ongoing" -> {
                statusBadge.background = ContextCompat.getDrawable(requireContext(), R.drawable.badge_ongoing)
                statusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.blue))
            }
            "resolved" -> {
                statusBadge.background = ContextCompat.getDrawable(requireContext(), R.drawable.badge_resolved)
                statusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
            }
        }
    }

    private fun checkOfficerStatus(view: View) {
        lifecycleScope.launch {
            val officerName: String? = withContext(Dispatchers.IO) {
                MySQLHelper.getOfficerNameByLocationID(locationID)
            }

            hideLoadingState()
            updateOfficerStatusUI(officerName)

            val currentStatus = arguments?.getString("STATUS")

            // If this is a resolved request the officer is returning to for reporting
            if (currentStatus?.lowercase() == "resolved") {
                isResolved = true
                showPostResolveState()
                return@launch
            }

            if (officerName != null && currentStatus == "pending") {
                view.findViewById<TextView>(R.id.status_badge)?.apply {
                    text = "ONGOING"
                    background = ContextCompat.getDrawable(requireContext(), R.drawable.badge_ongoing)
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.blue))
                }
            }
        }
    }

    private fun updateOfficerStatusUI(officerName: String?) {
        if (!officerName.isNullOrEmpty()) {
            hasOfficerResponded = true
            officerStatusCard.visibility = View.VISIBLE

            if (officerName == UserSingleton.fullName) {
                officerStatusText.text = "You are responding to this assistance request"
                officerStatusCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.lightBlue))

                if (!isResolved) {
                    showOfficerControls()
                    hideRespondButton()
                }
            } else {
                officerStatusText.text = "Officer $officerName is responding to this request"
                officerStatusCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.lightGray))
                hideAllControls()
            }
        } else {
            hasOfficerResponded = false
            officerStatusCard.visibility = View.GONE
            if (!isResolved) {
                showRespondButton()
                hideOfficerControls()
            }
        }
    }

    private fun showOfficerControls() {
        respondButton.visibility = View.GONE
        resolveButton.visibility = View.VISIBLE
        falseAlarmButton.visibility = View.VISIBLE

        val slideUpControls = AnimationUtils.loadAnimation(requireContext(), R.anim.slide_up_fade_in_controls)
        resolveButton.startAnimation(slideUpControls)
        falseAlarmButton.startAnimation(slideUpControls)
    }

    private fun hideOfficerControls() {
        resolveButton.visibility = View.GONE
        falseAlarmButton.visibility = View.GONE
    }

    private fun hideRespondButton() {
        respondButton.visibility = View.GONE
    }

    private fun hideAllControls() {
        respondButton.visibility = View.GONE
        resolveButton.visibility = View.GONE
        falseAlarmButton.visibility = View.GONE
    }

    private fun showRespondButton() {
        respondButton.visibility = View.VISIBLE
        resolveButton.visibility = View.GONE
        falseAlarmButton.visibility = View.GONE
    }

    private fun showLoadingState() {
        loadingProgress.visibility = View.VISIBLE
        respondButton.visibility = View.GONE
        resolveButton.visibility = View.GONE
        falseAlarmButton.visibility = View.GONE
    }

    private fun hideLoadingState() {
        loadingProgress.visibility = View.GONE
    }

    private fun setupClickListeners() {
        respondButton.setOnClickListener { respondToAssistance() }

        // Resolve immediately (no location picker)
        resolveButton.setOnClickListener { resolveAssistanceImmediately() }

        // False alarm immediately (no relocation)
        falseAlarmButton.setOnClickListener { submitFalseAlarm() }

        closeButton.setOnClickListener { dismiss() }

        makeReportButton.setOnClickListener { showReportSection() }

        postResolveCloseButton.setOnClickListener {
            dismiss()
            activity?.finish()
        }

        submitReportButton.setOnClickListener { submitReport() }
    }

    // --- Resolve immediately, then show post-resolve state ---
    private fun resolveAssistanceImmediately() {
        val officerName = UserSingleton.fullName ?: "Unknown Officer"
        val officerUserID = requireContext()
            .getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            .getString("userID", null).orEmpty()

        loadingProgress.visibility = View.VISIBLE
        resolveButton.isEnabled = false
        falseAlarmButton.isEnabled = false

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                MySQLHelper.resolveIncident(
                    locationID = locationID ?: "",
                    status = "resolved",
                    officerName = officerName,
                    officerUserID = officerUserID
                )
            }

            loadingProgress.visibility = View.GONE

            if (success) {
                isResolved = true
                showSuccessAnimation()
                sendDataChangeBroadcast()
                SmartPollingManager.getInstance().triggerFastUpdate()
                Toast.makeText(requireContext(), "Assistance resolved", Toast.LENGTH_SHORT).show()

                showPostResolveState()
            } else {
                resolveButton.isEnabled = true
                falseAlarmButton.isEnabled = true
                Toast.makeText(requireContext(), "Failed to resolve. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- False alarm: resolves immediately, no relocation ---
    private fun submitFalseAlarm() {
        val officerName = UserSingleton.fullName ?: "Unknown Officer"
        val officerUserID = requireContext()
            .getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            .getString("userID", null).orEmpty()

        loadingProgress.visibility = View.VISIBLE
        resolveButton.isEnabled = false
        falseAlarmButton.isEnabled = false

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                MySQLHelper.resolveIncident(
                    locationID = locationID ?: "",
                    status = "false alarm",
                    officerName = officerName,
                    officerUserID = officerUserID
                )
            }

            loadingProgress.visibility = View.GONE

            if (success) {
                showSuccessAnimation()
                sendDataChangeBroadcast()
                SmartPollingManager.getInstance().triggerFastUpdate()
                Toast.makeText(requireContext(), "Marked as false alarm", Toast.LENGTH_SHORT).show()
                dismiss()
                activity?.finish()
            } else {
                resolveButton.isEnabled = true
                falseAlarmButton.isEnabled = true
                Toast.makeText(requireContext(), "Failed. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- Show post-resolve state: Make Report + Close ---
    private fun showPostResolveState() {
        // Update badge to resolved
        view?.findViewById<TextView>(R.id.status_badge)?.apply {
            text = "RESOLVED"
            background = ContextCompat.getDrawable(requireContext(), R.drawable.badge_resolved)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
        }

        // Hide action buttons, show post-resolve buttons
        actionButtonsContainer.visibility = View.GONE
        postResolveButtons.visibility = View.VISIBLE
    }

    // --- Show report section with location chips ---
    private fun showReportSection() {
        postResolveButtons.visibility = View.GONE
        reportSection.visibility = View.VISIBLE
        setupReportLocationChips()
    }

    private fun setupReportLocationChips() {
        reportLocationChips.removeAllViews()
        reportLocationOptions.forEach { option ->
            val chip = createChip(option)
            chip.setOnClickListener { selectReportLocation(option) }
            reportLocationChips.addView(chip)
        }
    }

    private fun selectReportLocation(location: String) {
        selectedReportLocation = location
        updateChipSelectionStates(reportLocationChips, location)

        if (location == "Other") {
            reportOtherSection.visibility = View.VISIBLE
        } else {
            reportOtherSection.visibility = View.GONE
            reportActionFA.setText("")
            reportActionInfo.setText("")
        }

        submitReportButton.isEnabled = true
        submitReportButton.alpha = 1f
    }

    private fun submitReport() {
        val location = selectedReportLocation
        if (location.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Please select a location.", Toast.LENGTH_SHORT).show()
            return
        }

        val actionFA = if (location == "Other") reportActionFA.text.toString().trim() else null
        val actionInfo = if (location == "Other") reportActionInfo.text.toString().trim() else null

        submitReportButton.isEnabled = false
        loadingProgress.visibility = View.VISIBLE

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                MySQLHelper.submitIncidentReport(
                    locationID = locationID ?: "",
                    relocatedLocation = location,
                    actionFA = actionFA,
                    actionINFO = actionInfo
                )
            }

            loadingProgress.visibility = View.GONE

            if (success) {
                sendDataChangeBroadcast()
                SmartPollingManager.getInstance().triggerFastUpdate()
                Toast.makeText(requireContext(), "Report submitted successfully.", Toast.LENGTH_SHORT).show()
                dismiss()
                activity?.finish()
            } else {
                submitReportButton.isEnabled = true
                Toast.makeText(requireContext(), "Failed to submit report. Try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- Respond ---
    private fun respondToAssistance() {
        respondProgress.visibility = View.VISIBLE
        respondButton.isEnabled = false

        val officerName = UserSingleton.fullName ?: "Unknown Officer"
        val officerUserID = requireContext()
            .getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            .getString("userID", null).orEmpty()

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                MySQLHelper.updateIncidentResponse(
                    locationID = locationID ?: "",
                    status = "ongoing",
                    officerName = officerName,
                    officerUserID = officerUserID
                )
            }

            respondProgress.visibility = View.GONE
            respondButton.isEnabled = true

            if (success) {
                updateOfficerStatusUI(officerName)
                showSuccessAnimation()
                sendDataChangeBroadcast()
                SmartPollingManager.getInstance().triggerFastUpdate()
                (activity as? MapActivity)?.onOfficerResponded()
                Toast.makeText(requireContext(), "You are now responding to this assistance request", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Failed to respond. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- Chip helpers (reused from LocationPickerDialog pattern) ---
    private fun createChip(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.primaryColor))
            background = ContextCompat.getDrawable(context, R.drawable.chip_selector_background)
            isSelected = false
            isClickable = true
            isFocusable = true

            try {
                typeface = ResourcesCompat.getFont(context, R.font.inter_bold)
            } catch (_: Exception) {
                setTypeface(typeface, Typeface.BOLD)
            }

            layoutParams = FlexboxLayout.LayoutParams(
                FlexboxLayout.LayoutParams.WRAP_CONTENT,
                FlexboxLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, dpToPx(8), dpToPx(8))
            }
        }
    }

    private fun updateChipSelectionStates(container: FlexboxLayout, selectedText: String) {
        for (i in 0 until container.childCount) {
            val chip = container.getChildAt(i) as? TextView ?: continue
            val selected = chip.text.toString() == selectedText
            chip.isSelected = selected
            chip.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (selected) R.color.white else R.color.primaryColor
                )
            )
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    // --- Utility ---
    private fun showSuccessAnimation() {
        val successView = view?.findViewById<View>(R.id.success_indicator)
        successView?.let {
            it.visibility = View.VISIBLE
            val slideUpSuccess = AnimationUtils.loadAnimation(requireContext(), R.anim.success_slide_up)
            it.startAnimation(slideUpSuccess)
        }
    }

    private fun sendDataChangeBroadcast() {
        val intent = Intent(requireContext(), DataChangeReceiver::class.java)
        intent.action = "com.capstone.navicamp.DATA_CHANGED"
        requireContext().sendBroadcast(intent)
    }

    private fun formatDateTime(dateTime: String?): String {
        return if (dateTime != null) {
            try {
                val inputFormat = SimpleDateFormat("MMMM dd, yyyy hh:mm a", Locale.getDefault())
                val outputFormat = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
                val date = inputFormat.parse(dateTime)
                outputFormat.format(date!!)
            } catch (_: Exception) {
                dateTime
            }
        } else {
            ""
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    companion object {
        fun newInstance(
            floorLevel: String,
            locationID: String,
            userID: String,
            fullName: String,
            dateTime: String,
            status: String,
            alertID: String
        ): AssistanceModalDialog {
            val args = Bundle()
            args.putString("FLOOR_LEVEL", floorLevel)
            args.putString("LOCATION_ID", locationID)
            args.putString("USER_ID", userID)
            args.putString("FULL_NAME", fullName)
            args.putString("DATE_TIME", dateTime)
            args.putString("STATUS", status)
            args.putString("ALERT_ID", alertID)

            val fragment = AssistanceModalDialog()
            fragment.arguments = args
            return fragment
        }
    }
}
