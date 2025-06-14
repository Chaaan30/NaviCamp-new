package com.capstone.navicamp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class BatteryOptimizationFragment : Fragment() {
    
    private lateinit var statusIcon: ImageView
    private lateinit var statusText: TextView
    private lateinit var btnAction: Button
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_battery_optimization, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        statusIcon = view.findViewById(R.id.statusIcon)
        statusText = view.findViewById(R.id.statusText)
        btnAction = view.findViewById(R.id.btnAction)

        btnAction.setOnClickListener {
            if (BatteryOptimizationHelper.isIgnoringBatteryOptimizations(requireContext())) {
                (activity as? SetupActivity)?.moveToNextPage()
            } else {
                BatteryOptimizationHelper.showGeneralInstructions(requireContext())
            }
        }
        
        updateBatteryOptimizationStatus()
    }
    
    override fun onResume() {
        super.onResume()
        if (isAdded) {
            updateBatteryOptimizationStatus()
        }
    }
    
    private fun updateBatteryOptimizationStatus() {
        val isOptimized = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(requireContext())
        
        if (isOptimized) {
            statusIcon.setImageResource(R.drawable.ic_check_circle)
            statusIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.green))
            statusText.text = "Background Activity Allowed"
            statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
            btnAction.text = "Next"
        } else {
            statusIcon.setImageResource(R.drawable.ic_battery_alert)
            statusIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.orange))
            statusText.text = "Allow background activity for notifications"
            statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.orange))
            btnAction.text = "Allow Background Activity"
        }
    }
} 