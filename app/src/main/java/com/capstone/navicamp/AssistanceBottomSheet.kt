package com.capstone.navicamp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
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
import android.app.Dialog
import android.content.Intent

class AssistanceBottomSheet : BottomSheetDialogFragment() {

    private lateinit var respondButton: Button
    private lateinit var resolveButton: Button
    private var locationID: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_assistance, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

        if (status?.contains("ongoing") == true) {
            respondButton.isEnabled = false
            resolveButton.visibility = View.VISIBLE
        }

        respondButton.setOnClickListener {
            Log.d("AssistanceBottomSheet", "Respond button clicked")
            updateStatus(locationID, "ongoing")
            respondButton.isEnabled = false
            resolveButton.visibility = View.VISIBLE
        }

        resolveButton.setOnClickListener {
            Log.d("AssistanceBottomSheet", "Resolve button clicked")
            updateStatus(locationID, "resolved")
            activity?.finish() // Close MapActivity
        }
    }

    private fun updateStatus(locationID: String?, newStatus: String) {
        if (locationID != null) {
            val officerName = UserSingleton.fullName ?: "Unknown Officer"
            val statusWithOfficer = "$newStatus by Officer: $officerName"

            CoroutineScope(Dispatchers.IO).launch {
                val success = MySQLHelper.updateStatus(locationID, statusWithOfficer)
                withContext(Dispatchers.Main) {
                    if (success) {
                        view?.findViewById<TextView>(R.id.status_text)?.text = "Status: $newStatus"
                        Log.d("AssistanceBottomSheet", "Status updated to $statusWithOfficer")

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