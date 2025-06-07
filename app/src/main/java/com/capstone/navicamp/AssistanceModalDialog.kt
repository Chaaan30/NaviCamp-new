package com.capstone.navicamp

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import com.google.android.material.card.MaterialCardView
import android.view.animation.AnimationUtils
import android.graphics.Color
import android.graphics.drawable.ColorDrawable

class AssistanceModalDialog : DialogFragment() {

    private lateinit var respondButton: Button
    private lateinit var resolveButton: Button
    private lateinit var respondProgress: ProgressBar
    private lateinit var falseAlarmButton: Button
    private lateinit var falseAlarmReason: EditText
    private lateinit var falseAlarmReasonLayout: com.google.android.material.textfield.TextInputLayout
    private lateinit var submitFalseAlarmButton: Button
    private lateinit var officerStatusCard: MaterialCardView
    private lateinit var officerStatusText: TextView
    private lateinit var loadingProgress: ProgressBar
    private var locationID: String? = null
    private var loadingDialog: AlertDialog? = null
    private var alertID: String? = null
    private var hasOfficerResponded = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_assistance_modal, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        initializeViews(view)
        
        // Add entrance animation - bottom to top fade to match FAB arrow up
        val slideUpFadeIn = AnimationUtils.loadAnimation(requireContext(), R.anim.slide_up_fade_in)
        view.startAnimation(slideUpFadeIn)

        // Get data from arguments
        locationID = arguments?.getString("LOCATION_ID")
        alertID = arguments?.getString("ALERT_ID")

        // Populate initial data
        populateViewData(view)
        
        // Show loading and hide all buttons initially
        showLoadingState()
        
        // Check officer status
        checkOfficerStatus(view)
        
        // Setup click listeners
        setupClickListeners()
    }

    private fun initializeViews(view: View) {
        respondButton = view.findViewById(R.id.respond_button)
        resolveButton = view.findViewById(R.id.resolve_button)
        respondProgress = view.findViewById(R.id.respond_progress)
        falseAlarmButton = view.findViewById(R.id.false_alarm_button)
        falseAlarmReason = view.findViewById(R.id.false_alarm_reason)
        falseAlarmReasonLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.false_alarm_reason_layout)
        submitFalseAlarmButton = view.findViewById(R.id.submit_false_alarm_button)
        officerStatusCard = view.findViewById(R.id.officer_status_card)
        officerStatusText = view.findViewById(R.id.officer_status_text)
        loadingProgress = view.findViewById(R.id.respond_progress)
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
        
        // Style status badge based on status
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
            val officerName = withContext(Dispatchers.IO) {
                // Always fetch the latest officer status from database
                MySQLHelper.getOfficerNameByLocationID(locationID)
            }

            // Hide loading and update UI
            hideLoadingState()
            updateOfficerStatusUI(officerName)
            
            // Also update the status badge based on current status
            val currentStatus = arguments?.getString("STATUS")
            if (officerName != null && currentStatus == "pending") {
                // Update status badge to reflect that someone has responded
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
                // Current officer has responded
                officerStatusText.text = "You are responding to this assistance request"
                officerStatusCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.lightBlue))
                
                // Show control buttons, hide respond button
                showOfficerControls()
                hideRespondButton()
            } else {
                // Another officer has responded
                officerStatusText.text = "Officer $officerName is responding to this request"
                officerStatusCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.lightGray))
                
                // Hide all control buttons including respond button
                hideAllControls()
            }
        } else {
            // No officer has responded
            hasOfficerResponded = false
            officerStatusCard.visibility = View.GONE
            showRespondButton()
            hideOfficerControls()
        }
    }

    private fun showOfficerControls() {
        respondButton.visibility = View.GONE
        resolveButton.visibility = View.VISIBLE
        falseAlarmButton.visibility = View.VISIBLE
        
        // Add slide-up animation for controls
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
        submitFalseAlarmButton.visibility = View.GONE
    }

    private fun showRespondButton() {
        respondButton.visibility = View.VISIBLE
        resolveButton.visibility = View.GONE
        falseAlarmButton.visibility = View.GONE
        submitFalseAlarmButton.visibility = View.GONE
    }
    
    private fun showLoadingState() {
        loadingProgress.visibility = View.VISIBLE
        respondButton.visibility = View.GONE
        resolveButton.visibility = View.GONE
        falseAlarmButton.visibility = View.GONE
        submitFalseAlarmButton.visibility = View.GONE
    }
    
    private fun hideLoadingState() {
        loadingProgress.visibility = View.GONE
    }

    private fun setupClickListeners() {
        respondButton.setOnClickListener {
            respondToAssistance()
        }

        resolveButton.setOnClickListener {
            resolveAssistance("resolved")
        }

        falseAlarmButton.setOnClickListener {
            toggleFalseAlarmInput()
        }
        
        submitFalseAlarmButton.setOnClickListener {
            submitFalseAlarm()
        }
        
        // Close button
        view?.findViewById<Button>(R.id.close_button)?.setOnClickListener {
            dismiss()
        }
    }

    private fun respondToAssistance() {
        respondProgress.visibility = View.VISIBLE
        respondButton.isEnabled = false

        val officerName = UserSingleton.fullName ?: "Unknown Officer"

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                MySQLHelper.updateIncidentResponse(locationID ?: "", "ongoing", officerName)
            }

            respondProgress.visibility = View.GONE
            respondButton.isEnabled = true

            if (success) {
                // Update UI to show officer has responded
                updateOfficerStatusUI(officerName)
                
                // Show success animation
                showSuccessAnimation()
                
                // Send broadcast and trigger updates
                sendDataChangeBroadcast()
                SmartPollingManager.getInstance().triggerFastUpdate()
                
                Toast.makeText(requireContext(), "You are now responding to this assistance request", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Failed to respond. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resolveAssistance(status: String, description: String? = null) {
        val officerName = UserSingleton.fullName ?: "Unknown Officer"

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                MySQLHelper.resolveIncident(locationID ?: "", status, officerName, description)
            }

            if (success) {
                showSuccessAnimation()
                sendDataChangeBroadcast()
                SmartPollingManager.getInstance().triggerFastUpdate()
                
                val message = if (status == "false alarm") "Marked as false alarm" else "Assistance resolved"
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                
                // Close dialog and finish activity
                dismiss()
                activity?.finish()
            } else {
                Toast.makeText(requireContext(), "Failed to $status. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleFalseAlarmInput() {
        if (falseAlarmReasonLayout.visibility == View.VISIBLE) {
            // Hide the input and reset button
            falseAlarmReasonLayout.visibility = View.GONE
            submitFalseAlarmButton.visibility = View.GONE
            falseAlarmReason.text?.clear()
            falseAlarmButton.text = "False Alarm"
            falseAlarmButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_warning, 0, 0, 0)
        } else {
            // Show the input and change button
            falseAlarmReasonLayout.visibility = View.VISIBLE
            submitFalseAlarmButton.visibility = View.VISIBLE
            falseAlarmButton.text = "Cancel"
            falseAlarmButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_close, 0, 0, 0)
            
            // Focus and show keyboard
            falseAlarmReason.requestFocus()
            
            // Show keyboard automatically
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(falseAlarmReason, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }
    
    private fun submitFalseAlarm() {
        val reason = falseAlarmReason.text.toString().trim()
        
        if (reason.isEmpty()) {
            Toast.makeText(requireContext(), "Please provide a reason for marking this as false alarm", Toast.LENGTH_LONG).show()
            falseAlarmReason.requestFocus()
            return
        }
        
        resolveAssistance("false alarm", reason)
    }

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
                outputFormat.format(date)
            } catch (e: Exception) {
                dateTime
            }
        } else {
            ""
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        
        // Remove default background and title bar
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        // Set dialog properties
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        
        return dialog
    }

    override fun onStart() {
        super.onStart()
        // Set dialog size to be responsive
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