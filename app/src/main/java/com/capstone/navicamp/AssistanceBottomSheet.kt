import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.capstone.navicamp.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AssistanceBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_assistance, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val floorLevel = arguments?.getString("FLOOR_LEVEL")
        val userID = arguments?.getString("USER_ID")
        val fullName = arguments?.getString("FULL_NAME")
        val dateTime = arguments?.getString("DATE_TIME")
        val status = arguments?.getString("STATUS")

        view.findViewById<TextView>(R.id.floor_level_text).text = floorLevel
        view.findViewById<TextView>(R.id.user_id_text).text = userID
        view.findViewById<TextView>(R.id.full_name_text).text = fullName
        view.findViewById<TextView>(R.id.date_time_text).text = dateTime
        view.findViewById<TextView>(R.id.status_text).text = status
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                it.background = ContextCompat.getDrawable(requireContext(), R.drawable.rounded_bottom_sheet)
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_COLLAPSED
                behavior.peekHeight = BottomSheetBehavior.PEEK_HEIGHT_AUTO
                behavior.isDraggable = true
                behavior.isHideable = true

                behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(bottomSheet: View, newState: Int) {
                        // Handle state changes
                    }

                    override fun onSlide(bottomSheet: View, slideOffset: Float) {
                        val content = bottomSheet.findViewById<View>(R.id.content)
                        val pill = bottomSheet.findViewById<View>(R.id.bottom_sheet_pill)
                        content?.visibility = if (slideOffset < 0.5) View.GONE else View.VISIBLE
                        pill?.visibility = View.VISIBLE
                    }
                })
            }
        }
        dialog.window?.setDimAmount(0f)
        return dialog
    }


    companion object {
        fun newInstance(
            floorLevel: String,
            userID: String,
            fullName: String,
            dateTime: String,
            status: String
        ): AssistanceBottomSheet {
            val args = Bundle()
            args.putString("FLOOR_LEVEL", floorLevel)
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
