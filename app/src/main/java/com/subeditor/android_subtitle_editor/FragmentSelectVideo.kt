package com.subeditor.android_subtitle_editor

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import com.google.android.gms.ads.AdRequest
import com.subeditor.android_subtitle_editor.databinding.FragmentSelectVideoBinding

class SelectInternetVideoDialog: DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val input = EditText(requireContext())
        input.hint = "Enter a URL"
        val dd = android.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.enter_url))
            .setView(input)
            .setPositiveButton(getString(R.string.open)) { _, _ ->
                val newString = input.text.toString().trim()
                if (newString.startsWith("http") && Subs.videoExtensions.contains(newString.substringAfterLast("."))) {
                    setFragmentResult("requestVideoUrl", bundleOf("videoURL" to newString))
                } else {
                    setFragmentResult("requestVideoUrl", bundleOf("videoURL" to null))
                }
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                setFragmentResult("requestVideoUrl", bundleOf("videoURL" to null))
            }
        return dd.show()
    }
}

class FragmentSelectVideo : Fragment() {
    private var _binding: FragmentSelectVideoBinding? = null
    private val binding get() = _binding!!
    private val currentSub: CurrentSub by activityViewModels()
    private lateinit var toolbar : ToolBarHelper
    private fun activatePlayButton() {
            if (currentSub.actualVideoLocation.isNotEmpty())  {
                binding.fileInformation.text = currentSub.actualVideoLocation
                binding.playVideoWithCurrent.visibility = View.VISIBLE
                binding.playVideoWithCurrent.setOnClickListener {
                if (currentSub.videoLocatedAt == CurrentSub.LAN) {
                    currentSub.initiateServer(this)
                } else {
                    findNavController().navigate(R.id.fragmentNewPlayer)
                }
            }
        }
    }
    private var resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK && result.data?.data is Uri) {
            if (result.data?.data != null) {
                context?.contentResolver?.takePersistableUriPermission(result.data?.data!!, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val actualLocation = FileUtils(requireContext()).getPath(result.data?.data!!).toString()
                currentSub.addVideoLocation(result.data!!.data, CurrentSub.URI, CurrentSub.LOCAL, actualLocation, this)
                binding.fileInformation.text = actualLocation
                PlayData.currentPosition = 0
                PlayData.currentAudioTrack = 0
                activatePlayButton()
            }
        }}
    private fun loadVideo(location: String) {
        val intent = Intent()
            .setAction(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.type = "video/*"
        if (location != "") {
            intent.putExtra("android.provider.extra.INITIAL_URI", location)
        }
        resultLauncher.launch(Intent.createChooser(intent, getString(R.string.select_video)))
    }
    private fun askForVideoUrl() {
        SelectInternetVideoDialog().show(childFragmentManager, "")
        childFragmentManager.setFragmentResultListener("requestVideoUrl", this)
        { _, bundle ->
            val response = bundle.getString("videoURL")
            if (response != null) {
                currentSub.addVideoLocation(
                    response,
                    CurrentSub.STRING,
                    CurrentSub.INTERNET,
                    response,
                    this
                )
                activatePlayButton()
            }
        }
    }
    private fun openLanVideo(startLocation: String) {
        val action = FragmentSelectVideoDirections.actionFragmentSelectVideoToFragmentSMBExplorer(
            savingLocation = false,
            listAllFiles = false,
            extensions = Subs.videoExtensions,
            startLocation = startLocation,
            request = "OpenVideo",
            showToolbar = true
        )
        findNavController().navigate(action)
    }
    private fun activateOpenDirectoryButton() {
        if (currentSub.subtitleLocatedAt == CurrentSub.LAN) {
            binding.openDirectoryOfSub.visibility = View.VISIBLE
        } else if (currentSub.subtitleLocatedAt == CurrentSub.LOCAL && currentSub.actualSubtitleLocation != "Unknown") {
            binding.openDirectoryOfSub.visibility = View.VISIBLE
        }
    }
    private fun selectVideoFromDirectory() {
        if (currentSub.subtitleLocatedAt == CurrentSub.LAN) {
            openLanVideo(currentSub.subtitleLocation.toString().substringBeforeLast("/") + "/")
        } else {
            loadVideo(currentSub.subtitleLocation.toString())
        }
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSelectVideoBinding.inflate(inflater, container, false)
        val view = binding.root
        activateOpenDirectoryButton()
        binding.selectVideoLocal.setOnClickListener {
            loadVideo("")
        }
        binding.openDirectoryOfSub.setOnClickListener{
            selectVideoFromDirectory()
        }
        binding.selectVideoSmb.setOnClickListener {
            openLanVideo("")
        }
        binding.insertStream.setOnClickListener {
            askForVideoUrl()
        }
        activatePlayButton()
        toolbar = ToolBarHelper()
        toolbar.getToolbar(this)
        toolbar.hideItems(listOf(R.id.undo_toolbar, R.id.redo_toolbar, R.id.play_toolbar, R.id.help_toolbar))
        toolbar.addDescription(getString(R.string.FragmentShiftStretch))
        val adRequest = AdRequest.Builder().build()
        binding.adViewSelect.loadAd(adRequest)
        return view
    }
    override fun onResume() {
        toolbar.showToolbar()
        super.onResume()
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}