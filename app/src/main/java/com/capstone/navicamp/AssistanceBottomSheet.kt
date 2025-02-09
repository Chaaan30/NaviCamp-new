package com.capstone.navicamp

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class AssistanceBottomSheet : BottomSheetDialogFragment() {

    private lateinit var respondButton: Button
    private lateinit var resolveButton: Button
    private lateinit var respondProgress: ProgressBar
    private lateinit var falseAlarmButton: Button
    private lateinit var falseAlarmReason: EditText
    private var locationID: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_assistance, container, false)

        // Initialize views
        falseAlarmButton = view.findViewById(R.id.false_alarm_button)
        falseAlarmReason = view.findViewById(R.id.false_alarm_reason)

        // Set up click listener for the False Alarm button
        falseAlarmButton.setOnClickListener {
            toggleFalseAlarmReason()
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val officerName = fetchOfficerName(locationID)
        
        val officerTextView = view.findViewById<TextView>(R.id.officer_text)
        if (!officerName.isNullOrEmpty()) {
            officerTextView.text = "Officer: $officerName"
            officerTextView.visibility = View.VISIBLE
        } else {
            officerTextView.visibility = View.GONE
        }

        val floorLevel = arguments?.getString("FLOOR_LEVEL")
        locationID = arguments?.getString("LOCATION_ID")
        val userID = arguments?.getString("USER_ID")
        val fullName = arguments?.getString("FULL_NAME")
        val dateTime = arguments?.getString("DATE_TIME")
        val status = arguments?.getString("STATUS")

        view.findViewById<TextView>(R.id.floor_level_text).text = "$floorLevel"
        view.findViewById<TextView>(R.id.user_id_text).text = "User ID: $userID"
        view.findViewById<TextView>(R.id.full_name_text).text = "Full Name: $fullName"
        view.findViewById<TextView>(R.id.date_time_text).text = "Date and Time: ${formatDateTime(dateTime)}"
        view.findViewById<TextView>(R.id.status_text).text = "Status: $status"

        respondButton = view.findViewById(R.id.respond_button)
        resolveButton = view.findViewById(R.id.resolve_button)
        respondProgress = view.findViewById(R.id.respond_progress)

        if (status?.contains("ongoing") == true) {
            respondButton.isEnabled = false
            resolveButton.visibility = View.VISIBLE
        }

        respondButton.setOnClickListener {
            Log.d("AssistanceBottomSheet", "Respond button clicked")
            respondProgress.visibility = View.VISIBLE
            updateStatus(locationID, "ongoing")
        }

        resolveButton.setOnClickListener {
            Log.d("AssistanceBottomSheet", "Resolve button clicked")
            updateStatus(locationID, "resolved")
            Toast.makeText(requireContext(), "Assistance has been resolved", Toast.LENGTH_SHORT).show()
            activity?.finish() // Close MapActivity
        }
    }

    private fun updateStatus(locationID: String?, newStatus: String) {
        if (locationID != null) {
            val officerName = UserSingleton.fullName ?: "Unknown Officer"
    
            CoroutineScope(Dispatchers.IO).launch {
                val success = MySQLHelper.updateStatusAndOfficer(locationID, newStatus, officerName)
                withContext(Dispatchers.Main) {
                    respondProgress.visibility = View.GONE
                    if (success) {
                        view?.findViewById<TextView>(R.id.status_text)?.text = "Status: $newStatus"
                        Log.d("AssistanceBottomSheet", "Status updated to $newStatus by $officerName")
                        respondButton.isEnabled = false
                        resolveButton.visibility = View.VISIBLE
    
                        // Send broadcast to notify data change
                        val intent = Intent("com.capstone.navicamp.DATA_CHANGED")
                        requireContext().sendBroadcast(intent)
                    } else {
                        Log.e("AssistanceBottomSheet", "Failed to update status")
                    }
                }
            }
        } else {
            Log.e("AssistanceBottomSheet", "Location ID is null")
        }
    }

    private fun formatDateTime(dateTime: String?): String {
        return if (dateTime != null) {
            val inputFormat = SimpleDateFormat("MMMM dd, yyyy hh:mm a", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMMM dd, yyyy hh:mm a", Locale.getDefault())
            val date = inputFormat.parse(dateTime)
            outputFormat.format(date)
        } else {
            ""
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                it.background = ContextCompat.getDrawable(requireContext(), R.drawable.rounded_bottom_sheet)
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.peekHeight = resources.getDimensionPixelSize(R.dimen.bottom_sheet_peek_height)
                behavior.isDraggable = true
                behavior.isHideable = true

                behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(bottomSheet: View, newState: Int) {
                        // Handle state changes
                    }

                    override fun onSlide(bottomSheet: View, slideOffset: Float) {
                        // Handle slide changes
                    }
                })
            }
        }
        return dialog
    }

    private fun toggleFalseAlarmReason() {
        if (falseAlarmReason.visibility == View.VISIBLE) {
            falseAlarmReason.visibility = View.GONE
            falseAlarmButton.text = "False Alarm"
        } else {
            falseAlarmReason.visibility = View.VISIBLE
            falseAlarmButton.text = "Cancel False Alarm"
        }
    }

    private fun fetchOfficerName(locationID: String?): String? {
        return MySQLHelper.getOfficerNameByLocationID(locationID)
    }

    companion object {
        fun newInstance(
            floorLevel: String,
            locationID: String,
            userID: String,
            fullName: String,
            dateTime: String,
            status: String
        ): AssistanceBottomSheet {
            val args = Bundle()
            args.putString("FLOOR_LEVEL", floorLevel)
            args.putString("LOCATION_ID", locationID)
            args.putString("USER_ID", userID)
            args.putString("FULL_NAME", fullName)
            args.putString("DATE_TIME", dateTime)
            args.putString("STATUS", status)
            val fragment = AssistanceBottomSheet()
            fragment.arguments = args
            return fragment
        }
    }
}