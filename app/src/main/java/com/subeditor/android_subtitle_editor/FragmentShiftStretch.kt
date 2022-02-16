package com.subeditor.android_subtitle_editor

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import com.subeditor.android_subtitle_editor.databinding.FragmentShiftStretchBinding

class FragmentShiftStretch : Fragment() {
    private var _binding: FragmentShiftStretchBinding? = null
    private val binder get() = _binding!!
    lateinit var toolbar: ToolBarHelper
    private val currentSub: CurrentSub by activityViewModels()
    private fun updateText() {
        val startTimeInCategories = currentSub.subtitle.getLine(0).makeMapOfTimes()
        val endTimeInCategories = currentSub.subtitle.getLine(currentSub.subtitle.size()-1).makeMapOfTimes()
        binder.startHours.setText(startTimeInCategories[Subs.HOURS])
        binder.startMinutes.setText(startTimeInCategories[Subs.MINUTES])
        binder.startSeconds.setText(startTimeInCategories[Subs.SECONDS])
        binder.startMilliseconds.setText(startTimeInCategories[Subs.MILLISECONDS])
        binder.endHours.setText(endTimeInCategories[Subs.HOURS])
        binder.endMinutes.setText(endTimeInCategories[Subs.MINUTES])
        binder.endSeconds.setText(endTimeInCategories[Subs.SECONDS])
        binder.endMilliseconds.setText(endTimeInCategories[Subs.MILLISECONDS])
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShiftStretchBinding.inflate(inflater, container, false)
        val view = binder.root
        binder.syncToAsStartTime.setOnClickListener {
            try {
                val hours = binder.startHours.text.toString().toInt()
                val minutes = binder.startMinutes.text.toString().toInt()
                val seconds = binder.startSeconds.text.toString().toInt()
                val milli = binder.startMilliseconds.text.toString().toInt()
                currentSub.subtitle.giveNewStartTimeInSeparateTimeUnits(hours, minutes, seconds, milli)
            } catch (e:Exception) {
                Toast.makeText(requireContext(), getString(R.string.please_enter_a_valid_number), Toast.LENGTH_SHORT).show()
            }
        }
        binder.syncToAsEndTime.setOnClickListener {
            try {
                val hours = binder.endHours.text.toString().toInt()
                val minutes = binder.endMinutes.text.toString().toInt()
                val seconds = binder.endSeconds.text.toString().toInt()
                val milli = binder.endMilliseconds.text.toString().toInt()
                currentSub.subtitle.giveNewEndTimeInSeparateTimeUnits(hours, minutes, seconds, milli)
            } catch (e:Exception) {
                Toast.makeText(requireContext(), getString(R.string.please_enter_a_valid_number), Toast.LENGTH_SHORT).show()
            }
        }
        currentSub.subtitle.subtitleChange.observe(viewLifecycleOwner) {
            updateText()
            currentSub.backupSession(this, it)
        }
        binder.shiftBackwardsTo.setOnClickListener {
            try {
                val shift = binder.shiftBackwards.text.toString().toInt()
                currentSub.subtitle.shift(shift * -1)
            } catch (e:Exception) {
                Toast.makeText(requireContext(), getString(R.string.please_enter_a_valid_number), Toast.LENGTH_SHORT).show()
            }
        }
        binder.shiftForwardsTo.setOnClickListener {
            try {
                val shift  = binder.shiftForwards.text.toString().toInt()
                currentSub.subtitle.shift(shift)
            } catch (e:Exception) {
                Toast.makeText(requireContext(), getString(R.string.please_enter_a_valid_number), Toast.LENGTH_SHORT).show()
            }
        }
        toolbar = ToolBarHelper()
        toolbar.getToolbar(this)
        toolbar.addDescription(getString(R.string.FragmentShiftStretch))
        return view
    }
    override fun onResume() {
        toolbar.showToolbar()
        super.onResume()
    }
    override fun onDetach() {
        if (this::toolbar.isInitialized) {
            toolbar.hideToolbar()
        }
        super.onDetach()
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}