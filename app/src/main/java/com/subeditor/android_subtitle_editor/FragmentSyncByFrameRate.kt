package com.subeditor.android_subtitle_editor

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import com.subeditor.android_subtitle_editor.databinding.FragmentSyncByFrameRateBinding
import java.lang.NumberFormatException

class FragmentSyncByFrameRate : Fragment() {
    private var _binding: FragmentSyncByFrameRateBinding? = null
    private val binding get() = _binding!!
    private var before : Double = 0.0
    private var after : Double = 0.0
    private var chosenFactor = 1.0
    private lateinit var toolbar: ToolBarHelper
    private val currentSub: CurrentSub by activityViewModels()
    private fun loadButtons() {
        binding.beforeChoice.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.before23_975 -> showFactor(23.975, after)
                    R.id.before24 -> showFactor(24.0, after)
                    R.id.before25 -> showFactor(25.0, after)
                    R.id.before29_975 -> showFactor(29.975, after)
                    R.id.before30 -> showFactor(30.0, after)
                    R.id.custom_before -> binding.customBefore1.visibility = View.VISIBLE
                }
            }
        }
        binding.afterChoice.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.after23_975 -> showFactor(before, 23.975)
                    R.id.after24 -> showFactor(before, 24.0)
                    R.id.after25 -> showFactor(before, 25.0)
                    R.id.after29_975 -> showFactor(before, 29.975)
                    R.id.after30 -> showFactor(before, 30.0)
                    R.id.custom_after -> binding.customAfter1.visibility = View.VISIBLE
                }
            }
        }
        binding.convert.setOnClickListener {
            if (chosenFactor != 0.0) {
                currentSub.subtitle.stretch(chosenFactor)
                val dd = Toast.makeText(
                    requireContext(),
                    String.format(
                        getString(R.string.subtitle_has_been_synced_to),
                        after.toString()
                    ),
                    Toast.LENGTH_LONG
                )
                dd.show()
            } else {
                Toast.makeText(requireContext(), getString(R.string.chose_two_frames), Toast.LENGTH_SHORT).show()
            }
        }
        binding.customAfter1.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                try {
                    val kk = s.toString().toDouble()
                    if (kk > 1)
                        showFactor(before, kk)
                } catch (e: NumberFormatException) {}
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        )
        binding.customBefore1.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                try {
                    val kk = s.toString().toDouble()
                    if (kk > 1)
                        showFactor(kk, after)
                } catch (e: NumberFormatException) {
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        )
    }
    fun showFactor(newBefore: Double, newAfter: Double) {
        if (newBefore != before) {
            before = newBefore
        }
        if (newAfter != after) {
            after = newAfter
        }
        if (!(before == 0.0 || after == 0.0)) {
            val newFactor = before / after
            if (newFactor != chosenFactor) {
                chosenFactor = newFactor
                val formatted = String.format("%.6f", chosenFactor.toFloat())
                binding.factor.text = formatted
            }
        }
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSyncByFrameRateBinding.inflate(inflater, container, false)
        val view = binding.root
        loadButtons()
        toolbar = ToolBarHelper()
        toolbar.getToolbar(this)
        toolbar.hideItems(listOf(R.id.undo_toolbar, R.id.redo_toolbar, R.id.help_toolbar))
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