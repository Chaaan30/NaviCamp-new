package com.capstone.navicamp

import android.animation.ObjectAnimator
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class IncidentCardAdapter(
    private val context: Context,
    private var incidents: List<IncidentData>,
    private val onMapClick: (IncidentData) -> Unit,
    private val onResolveClick: (IncidentData) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_DATE_HEADER = 0
        private const val TYPE_INCIDENT = 1
    }

    sealed class ListItem {
        data class DateHeader(val date: String) : ListItem()
        data class IncidentItem(val incident: IncidentData) : ListItem()
    }

    private var listItems: List<ListItem> = emptyList()

    init {
        groupIncidentsByDate()
    }

    data class IncidentData(
        val alertId: String,
        val userId: String,
        val deviceId: String,
        val userName: String,
        val coordinates: String,
        val floorLevel: String,
        val status: String,
        val timeOfAlert: String,
        val resolvedOn: String?,
        val officerName: String?,
        val incidentDescription: String
    )

    // Date Header ViewHolder
    class DateHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateText: TextView = itemView.findViewById(R.id.date_header_text)
        
        fun bind(date: String) {
            dateText.text = date
        }
    }

    inner class IncidentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Collapsed view elements
        private val statusIndicator: View = itemView.findViewById(R.id.status_indicator)
        private val alertId: TextView = itemView.findViewById(R.id.alert_id)
        private val statusBadge: TextView = itemView.findViewById(R.id.status_badge)
        private val expandIcon: ImageView = itemView.findViewById(R.id.expand_icon)
        private val userName: TextView = itemView.findViewById(R.id.user_name)
        private val timeAgo: TextView = itemView.findViewById(R.id.time_ago)
        private val locationSummary: TextView = itemView.findViewById(R.id.location_summary)
        
        // Expanded view elements
        private val expandedContent: LinearLayout = itemView.findViewById(R.id.expanded_content)
        private val userId: TextView = itemView.findViewById(R.id.user_id)
        private val deviceId: TextView = itemView.findViewById(R.id.device_id)
        private val detailedLocation: TextView = itemView.findViewById(R.id.detailed_location)
        private val relocatedToLayout: LinearLayout = itemView.findViewById(R.id.relocated_to_layout)
        private val relocatedTo: TextView = itemView.findViewById(R.id.relocated_to)
        private val latitude: TextView = itemView.findViewById(R.id.latitude)
        private val longitude: TextView = itemView.findViewById(R.id.longitude)
        private val createdAt: TextView = itemView.findViewById(R.id.created_at)
        private val resolvedAt: TextView = itemView.findViewById(R.id.resolved_at)
        private val resolvedTimeLayout: LinearLayout = itemView.findViewById(R.id.resolved_time_layout)

        private var isExpanded = false

        fun bind(incident: IncidentData) {
            // Set collapsed view data
            alertId.text = incident.alertId
            userName.text = incident.userName
            
            // Show only floor level from wheelchair in summary (not relocated location)
            val relocatedLocationText = incident.incidentDescription.trim()
            locationSummary.text = "📍 ${incident.floorLevel}"
            
            // Set status and colors
            setStatus(incident.status)
            
            // Calculate time ago
            timeAgo.text = getTimeAgo(incident.timeOfAlert)
            
            // Set expanded view data
            userId.text = incident.userId
            deviceId.text = incident.deviceId
            detailedLocation.text = incident.floorLevel
            
            // Handle relocated location (show only if it's not empty)
            if (relocatedLocationText.isNotEmpty()) {
                relocatedToLayout.visibility = View.VISIBLE
                relocatedTo.text = relocatedLocationText
            } else {
                relocatedToLayout.visibility = View.GONE
            }
            
            // Parse coordinates
            val coords = formatCoordinates(incident.coordinates)
            val coordParts = coords.split(",")
            if (coordParts.size >= 2) {
                latitude.text = coordParts[0].trim()
                longitude.text = coordParts[1].trim()
            } else {
                latitude.text = "N/A"
                longitude.text = "N/A"
            }
            
            createdAt.text = formatDateTime(incident.timeOfAlert)
            
            // Handle resolved time
            if (incident.resolvedOn.isNullOrBlank()) {
                resolvedTimeLayout.visibility = View.GONE
            } else {
                resolvedTimeLayout.visibility = View.VISIBLE
                resolvedAt.text = formatDateTime(incident.resolvedOn)
            }
            
            // Set click listeners
            setupClickListeners(incident)
            
            // Reset expanded state for recycled views
            isExpanded = false
            expandedContent.visibility = View.GONE
            rotateExpandIcon(false)
        }

        private fun setStatus(status: String) {
            statusBadge.text = status.uppercase()
            
            when (status.lowercase()) {
                "ongoing", "pending" -> {
                    statusIndicator.setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_red_light))
                    statusBadge.setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
                }
                "in_progress", "responding" -> {
                    statusIndicator.setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_orange_light))
                    statusBadge.setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_orange_dark))
                }
                "resolved", "completed" -> {
                    statusIndicator.setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_green_light))
                    statusBadge.setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))
                }
                "false alarm" -> {
                    statusIndicator.setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                    statusBadge.setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                }
                else -> {
                    statusIndicator.setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                    statusBadge.setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                }
            }
        }

        private fun getTimeAgo(timeString: String): String {
            return try {
                val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val alertTime = format.parse(timeString)
                val currentTime = Date()
                
                if (alertTime != null) {
                    val diffInMillis = currentTime.time - alertTime.time
                    
                    when {
                        diffInMillis < TimeUnit.MINUTES.toMillis(1) -> "Just now"
                        diffInMillis < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diffInMillis)}m ago"
                        diffInMillis < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diffInMillis)}h ago"
                        else -> "${TimeUnit.MILLISECONDS.toDays(diffInMillis)}d ago"
                    }
                } else {
                    timeString
                }
            } catch (e: Exception) {
                timeString
            }
        }

        private fun getShortDescription(description: String): String {
            return if (description.length > 30) {
                "${description.take(30)}..."
            } else {
                description
            }
        }

        private fun formatCoordinates(coords: String): String {
            return try {
                val parts = coords.split(",")
                if (parts.size == 2) {
                    val lat = String.format("%.4f", parts[0].trim().toDouble())
                    val lng = String.format("%.4f", parts[1].trim().toDouble())
                    "$lat, $lng"
                } else {
                    coords
                }
            } catch (e: Exception) {
                coords
            }
        }
        
        private fun formatDateTime(dateTimeString: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val outputFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                val date = inputFormat.parse(dateTimeString)
                if (date != null) {
                    outputFormat.format(date)
                } else {
                    dateTimeString
                }
            } catch (e: Exception) {
                dateTimeString
            }
        }

        private fun setupClickListeners(incident: IncidentData) {
            // Card click to expand/collapse
            itemView.setOnClickListener {
                toggleExpansion()
            }
            
            // Long press for resolve functionality (as buttons were removed)
            itemView.setOnLongClickListener {
                if (incident.status.lowercase() == "ongoing") {
                    onResolveClick(incident)
                    true
                } else {
                    false
                }
            }
        }

        private fun toggleExpansion() {
            isExpanded = !isExpanded
            
            if (isExpanded) {
                // Expand
                expandedContent.visibility = View.VISIBLE
                expandedContent.alpha = 0f
                expandedContent.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start()
            } else {
                // Collapse
                expandedContent.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        expandedContent.visibility = View.GONE
                    }
                    .start()
            }
            
            rotateExpandIcon(isExpanded)
        }

        private fun rotateExpandIcon(expanded: Boolean) {
            val rotation = if (expanded) 180f else 0f
            ObjectAnimator.ofFloat(expandIcon, "rotation", rotation)
                .setDuration(200)
                .start()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (listItems[position]) {
            is ListItem.DateHeader -> TYPE_DATE_HEADER
            is ListItem.IncidentItem -> TYPE_INCIDENT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_DATE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_date_header, parent, false)
                DateHeaderViewHolder(view)
            }
            TYPE_INCIDENT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_incident_card_expandable, parent, false)
                IncidentViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is DateHeaderViewHolder -> {
                val dateHeader = listItems[position] as ListItem.DateHeader
                holder.bind(dateHeader.date)
            }
            is IncidentViewHolder -> {
                val incidentItem = listItems[position] as ListItem.IncidentItem
                holder.bind(incidentItem.incident)
            }
        }
    }

    override fun getItemCount(): Int = listItems.size

    fun updateIncidents(newIncidents: List<IncidentData>) {
        incidents = newIncidents
        groupIncidentsByDate()
        notifyDataSetChanged()
    }

    private fun groupIncidentsByDate() {
        val grouped = mutableListOf<ListItem>()
        
        // Sort incidents by date (newest first)
        val sortedIncidents = incidents.sortedByDescending { incident ->
            try {
                val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                format.parse(incident.timeOfAlert)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        }
        
        var currentDate = ""
        
        sortedIncidents.forEach { incident ->
            val incidentDate = formatDateForHeader(incident.timeOfAlert)
            
            if (incidentDate != currentDate) {
                currentDate = incidentDate
                grouped.add(ListItem.DateHeader(incidentDate))
            }
            
            grouped.add(ListItem.IncidentItem(incident))
        }
        
        listItems = grouped
    }
    
    private fun formatDateForHeader(dateTimeString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
            val date = inputFormat.parse(dateTimeString)
            if (date != null) {
                outputFormat.format(date)
            } else {
                dateTimeString
            }
        } catch (e: Exception) {
            dateTimeString
        }
    }

    fun filterIncidents(query: String, status: String) {
        // Implementation for filtering incidents
        // This would typically be done in the activity/fragment
    }
} 