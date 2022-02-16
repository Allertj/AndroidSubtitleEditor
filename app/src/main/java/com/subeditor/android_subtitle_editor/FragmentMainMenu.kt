package com.subeditor.android_subtitle_editor

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import com.google.android.gms.ads.AdRequest
import com.subeditor.android_subtitle_editor.databinding.FragmentMainMenuBinding

class ReloadDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dd = android.app.AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.load_different_subtitle_question))
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                setFragmentResult("ReloadQuestion", bundleOf("Reload" to true))
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                setFragmentResult("ReloadQuestion", bundleOf("Reload" to false))
            }
        return dd.show()
    }
}

class FragmentMainMenu : Fragment() {
    private var _binding: FragmentMainMenuBinding? = null
    private val binding get() = _binding!!
    private val currentSub: CurrentSub by activityViewModels()
    private fun handleStartupOrLoad() {
        if (currentSub.actualSubtitleLocation == "" && activity?.intent?.data == null) {
            val action = FragmentMainMenuDirections.actionFragmentMainMenuToFragmentLoadFile()
            findNavController().navigate(action)
        } else if (currentSub.actualSubtitleLocation == "" && activity?.intent?.data != null) {
            val action = FragmentMainMenuDirections.actionFragmentMainMenuToFragmentLoadFile("loadFromFile")
            findNavController().navigate(action)
        }
}
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainMenuBinding.inflate(inflater, container, false)
        val view = binding.root
        currentSub.setNightTheme(this)
        if (currentSub.subtitleLocation == null || activity?.intent?.data != null) {
            handleStartupOrLoad()
        }
        if (currentSub.subtitleLocation != null) {
            val toolbar = ToolBarHelper()
            toolbar.getToolbar(this)
            toolbar.hideToolbarCompletely()
        }
        binding.viewSubtitle.setOnClickListener {
            val action = FragmentMainMenuDirections.actionFragmentMainMenuToFragmentViewSubtitle()
            findNavController().navigate(action)
        }
        binding.editSubtitle.setOnClickListener {
            val action = FragmentMainMenuDirections.actionFragmentMainMenuToFragmentEdit()
            findNavController().navigate(action)
        }
        binding.shiftStretchSubtitle.setOnClickListener {
            val action = FragmentMainMenuDirections.actionFragmentMainMenuToFragmentShiftStretch()
            findNavController().navigate(action)
        }
        binding.selectVideo.setOnClickListener {
            val action = FragmentMainMenuDirections.actionFragmentMainMenuToFragmentSelectVideo()
            findNavController().navigate(action)
        }
        binding.playWithVideo.setOnClickListener {
            if (currentSub.videoLocation == null || currentSub.actualVideoLocation == "") {
                val action = FragmentMainMenuDirections.actionFragmentMainMenuToFragmentSelectVideo()
                findNavController().navigate(action)
            } else {
                if (currentSub.videoLocatedAt == CurrentSub.LAN) {
                    currentSub.initiateServer(this)
                } else {
                    findNavController().navigate(R.id.fragmentNewPlayer)
                }
            }
        }
        binding.syncByFrameRate.setOnClickListener {
            val action = FragmentMainMenuDirections.actionFragmentMainMenuToFragmentSyncByFrameRate()
            findNavController().navigate(action)
        }
        binding.syncWithOtherSubtitle.setOnClickListener {
            val action = FragmentMainMenuDirections.actionFragmentMainMenuToFragmentSyncWithOtherSub()
            findNavController().navigate(action)
        }
        binding.advanced.setOnClickListener {
            val action = FragmentMainMenuDirections.actionFragmentMainMenuToFragmentAdvanced()
            findNavController().navigate(action)
        }
        binding.saveSubtitle.setOnClickListener {
            val action = FragmentMainMenuDirections.actionFragmentMainMenuToFragmentSaveSubtitle()
            findNavController().navigate(action)
        }
        binding.reloadToolbarActionBackground.setOnClickListener{
            ReloadDialog().show(childFragmentManager, "")
            childFragmentManager.setFragmentResultListener("ReloadQuestion", this)
            {_, bundle ->
                if (bundle["Reload"] as Boolean) {
                    currentSub.wipeLastSession(this)
                    val action = FragmentMainMenuDirections.actionFragmentMainMenuToFragmentLoadFile()
                    findNavController().navigate(action)
                }
            }
        }
        val adRequest = AdRequest.Builder().build()
        binding.adView3.loadAd(adRequest)
        return view
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}