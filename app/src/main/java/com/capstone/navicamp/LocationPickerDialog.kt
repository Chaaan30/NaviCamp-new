package com.capstone.navicamp

import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.DialogFragment
import com.google.android.flexbox.FlexboxLayout

/**
 * Location picker dialog for selecting building, floor, and room.
 * Used when resolving assistance requests to specify user's relocated location.
 */
class LocationPickerDialog : DialogFragment() {

    private var onLocationSelected: ((String) -> Unit)? = null
    private var dialogTitle: String = "Location Update"

    private var selectedBuilding: String? = null
    private var selectedFloor: String? = null
    private var selectedRoom: String? = null

    private lateinit var buildingChipsContainer: FlexboxLayout
    private lateinit var floorSection: LinearLayout
    private lateinit var floorChipsContainer: FlexboxLayout
    private lateinit var roomSection: LinearLayout
    private lateinit var roomChipsContainer: FlexboxLayout
    private lateinit var doneButton: Button
    private lateinit var contentScrollView: androidx.core.widget.NestedScrollView

    // Building data structure: Building -> Floor -> Rooms
    // Using LinkedHashMap to maintain insertion order (floors sorted high to low, rooms sorted)
    private val buildingData = linkedMapOf(
        "Einstein Building" to linkedMapOf(
            "Fifth Floor" to listOf(
                "E501", "E503", "E505", "E506", "E507A", "E509", "E510A", "E511", "E512", "E513", "E515"
            ),
            "Fourth Floor" to listOf(
                "E402", "E404", "E406", "E408A", "E408B", "E409", "E410", "E411", "E412", "E413", "E414", "E415",
                "Global Classroom", "ETYCB/CHS Faculty", "ETYCB Office"
            ),
            "Third Floor" to listOf(
                "E301", "E303", "E305", "E311", "E312", "E313", "E315",
                "CCIS", "CGC College", "CGC SHS", "CMET", "Dean's Office CAS",
                "Faculty Center of Aviation", "Faculty Center of CCIS and CMET",
                "Faculty Center of Math and Science Cluster", "Student Affairs Office"
            ),
            "Second Floor" to listOf(
                "E202", "E204", "CLIR"
            ),
            "First Floor" to listOf(
                "E106", "E107", "E108", "E109", "E110", "E111", "E112", "E113", "E114",
                "Faculty Center MITL", "MITL"
            )
        ),
        "ETY Building" to linkedMapOf(
            "" to listOf( // No floor for ETY Building
                "Y200 - Cafe Enrique", "Y205A - Food Lab 1", "Y205B - IFO",
                "Board Room", "Office for External Relations and Global Linkings"
            )
        )
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_location_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupBuildingChips()
        setupDoneButton()

        // Update dialog title if provided
        view.findViewById<TextView>(R.id.dialog_title)?.text = dialogTitle
    }

    private fun initializeViews(view: View) {
        buildingChipsContainer = view.findViewById(R.id.building_chips_container)
        floorSection = view.findViewById(R.id.floor_section)
        floorChipsContainer = view.findViewById(R.id.floor_chips_container)
        roomSection = view.findViewById(R.id.room_section)
        roomChipsContainer = view.findViewById(R.id.room_chips_container)
        doneButton = view.findViewById(R.id.done_button)
        contentScrollView = view.findViewById(R.id.content_scroll_view)
    }
    
    // Constrain scroll view height when content grows too large
    private fun constrainScrollViewHeight() {
        val maxScrollHeight = (resources.displayMetrics.heightPixels * 0.45).toInt()
        contentScrollView.post {
            if (contentScrollView.height > maxScrollHeight) {
                val params = contentScrollView.layoutParams
                params.height = maxScrollHeight
                contentScrollView.layoutParams = params
            }
        }
    }

    private fun setupBuildingChips() {
        buildingChipsContainer.removeAllViews()

        buildingData.keys.forEach { building ->
            val chip = createChip(building)
            chip.setOnClickListener {
                selectBuilding(building)
            }
            buildingChipsContainer.addView(chip)
        }
    }

    private fun selectBuilding(building: String) {
        selectedBuilding = building
        selectedFloor = null
        selectedRoom = null

        // Update chip selection states
        updateChipSelectionStates(buildingChipsContainer, building)

        // Show/hide floor section based on building
        val floors = buildingData[building] ?: emptyMap()

        if (floors.keys.any { it.isNotEmpty() }) {
            // Building has floors
            setupFloorChips(floors.keys.filter { it.isNotEmpty() })
            floorSection.visibility = View.VISIBLE
            roomSection.visibility = View.GONE
        } else {
            // Building has no floors (ETY Building) - show rooms directly
            floorSection.visibility = View.GONE
            val rooms = floors[""] ?: emptyList()
            setupRoomChips(rooms)
            roomSection.visibility = View.VISIBLE
        }

        updateDoneButtonState()
        constrainScrollViewHeight()
    }

    private fun setupFloorChips(floors: Collection<String>) {
        floorChipsContainer.removeAllViews()

        floors.forEach { floor ->
            val chip = createChip(floor)
            chip.setOnClickListener {
                selectFloor(floor)
            }
            floorChipsContainer.addView(chip)
        }
    }

    private fun selectFloor(floor: String) {
        selectedFloor = floor
        selectedRoom = null

        // Update chip selection states
        updateChipSelectionStates(floorChipsContainer, floor)

        // Show rooms for selected floor
        val rooms = buildingData[selectedBuilding]?.get(floor) ?: emptyList()
        setupRoomChips(rooms)
        roomSection.visibility = View.VISIBLE

        updateDoneButtonState()
        constrainScrollViewHeight()
    }

    private fun setupRoomChips(rooms: List<String>) {
        roomChipsContainer.removeAllViews()

        rooms.forEach { room ->
            val chip = createChip(room)
            chip.setOnClickListener {
                selectRoom(room)
            }
            roomChipsContainer.addView(chip)
        }
    }

    private fun selectRoom(room: String) {
        selectedRoom = room
        updateChipSelectionStates(roomChipsContainer, room)
        updateDoneButtonState()
    }

    private fun createChip(text: String): TextView {
        val chip = TextView(requireContext()).apply {
            this.text = text
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.primaryColor))
            background = ContextCompat.getDrawable(context, R.drawable.chip_selector_background)
            isSelected = false
            isClickable = true
            isFocusable = true

            // Try to load custom font
            try {
                typeface = ResourcesCompat.getFont(context, R.font.inter_bold)
            } catch (e: Exception) {
                setTypeface(typeface, Typeface.BOLD)
            }

            // Chip margins
            val params = FlexboxLayout.LayoutParams(
                FlexboxLayout.LayoutParams.WRAP_CONTENT,
                FlexboxLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, dpToPx(8), dpToPx(8))
            }
            layoutParams = params
        }
        return chip
    }

    private fun updateChipSelectionStates(container: FlexboxLayout, selectedText: String) {
        for (i in 0 until container.childCount) {
            val chip = container.getChildAt(i) as? TextView
            chip?.let {
                val isSelected = it.text.toString() == selectedText
                it.isSelected = isSelected
                it.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (isSelected) R.color.white else R.color.primaryColor
                    )
                )
            }
        }
    }

    private fun updateDoneButtonState() {
        // Enable button only when all required selections are made
        val isComplete = selectedBuilding != null && selectedRoom != null &&
                (selectedFloor != null || buildingData[selectedBuilding]?.keys?.any { it.isEmpty() } == true)

        doneButton.isEnabled = isComplete
        doneButton.alpha = if (isComplete) 1f else 0.5f
    }

    private fun setupDoneButton() {
        doneButton.setOnClickListener {
            val locationString = buildLocationString()
            if (locationString.isNotEmpty()) {
                onLocationSelected?.invoke(locationString)
                dismiss()
            }
        }
    }

    private fun buildLocationString(): String {
        val building = selectedBuilding ?: return ""
        val room = selectedRoom ?: return ""

        return if (selectedFloor.isNullOrEmpty()) {
            // ETY Building (no floor)
            "$building - $room"
        } else {
            "$building - $selectedFloor - $room"
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
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
        // Set dialog width to 92% of screen, height wraps content
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    fun setOnLocationSelectedListener(listener: (String) -> Unit) {
        onLocationSelected = listener
    }

    fun setDialogTitle(title: String) {
        dialogTitle = title
    }

    companion object {
        fun newInstance(title: String = "Location Update"): LocationPickerDialog {
            return LocationPickerDialog().apply {
                dialogTitle = title
            }
        }
    }
}

