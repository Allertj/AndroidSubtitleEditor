package com.subeditor.android_subtitle_editor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.*
import androidx.navigation.findNavController
import com.subeditor.android_subtitle_editor.databinding.FragmentSyncWithOtherSubBinding

class FragmentSyncWithOtherSub : Fragment() {
    private var _binding: FragmentSyncWithOtherSubBinding? = null
    private val binding get() = _binding!!
    lateinit var toolbar: ToolBarHelper
    private val currentSub: CurrentSub by activityViewModels()
    private fun checkFileSize(selectedFile: Uri, fileSize: Long) {
        if (fileSize < Subs.IMPROBABLE_FILE_SIZE ) {
            openFile(selectedFile)
        } else {
            val askConfirmationFragment = AskConfirmationFragment()
            askConfirmationFragment.message = getString(R.string.ask_if_size_is_big)
            askConfirmationFragment.show(childFragmentManager, "")
            childFragmentManager.setFragmentResultListener("requestConfirm", this)
            { _, bundle ->
                val resultReceived = bundle.getBoolean("Confirmed")
                if (resultReceived) {
                    openFile(selectedFile)
                }
            }
        }
    }
    private fun openFile(selectedFile: Uri) {
        try {
            val results = requireContext().contentResolver.openInputStream(selectedFile)?.readBytes()
            val fileCheck = NewFileChecking(NewFileChecking.FOR_SYNC_OPENING_LOCAL, this)
            val decodedPath = FileUtils(requireContext()).getPath(selectedFile)
            fileCheck.checkSequence(this, decodedPath!!, results!!)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.an_error_has_occurred), Toast.LENGTH_LONG).show()
        }
    }
    private var resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        try {
            if (result.resultCode == AppCompatActivity.RESULT_OK && result.data?.data is Uri) {
                val selectedFile = result.data?.data
                val size = context?.contentResolver?.openAssetFileDescriptor(selectedFile!!, "r")?.length
                checkFileSize(selectedFile!!, size!!)

            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.an_error_has_occurred), Toast.LENGTH_LONG).show()
        }
    }
    fun subtitleLoaded(otherSubtitle: Subtitle) {
        ReceivedInfo.loadOtherSubtitle(otherSubtitle)
        updateText()
    }
    fun updateText() {
        binding.firstSubSelf.text = currentSub.subtitle.getLine(ReceivedInfo.getValue(ReceivedInfo.FIRST_SELF)).toSpannable()
        binding.lastSubSelf.text = currentSub.subtitle.getLine(ReceivedInfo.getValue(ReceivedInfo.LAST_SELF)).toSpannable()
        if (ReceivedInfo.otherSubInitialized) {
            binding.firstSubOther.text = ReceivedInfo.otherSubtitle.getLine(ReceivedInfo.getValue(ReceivedInfo.FIRST_OTHER)).toSpannable()
            binding.lastSubOther.text  = ReceivedInfo.otherSubtitle.getLine(ReceivedInfo.getValue(ReceivedInfo.LAST_OTHER)).toSpannable()
        }
    }
    private fun chooseSubtitle(typeAsked: Int, subtitle: Subtitle){
        val myFragment = FragmentEdit()
        myFragment.receive(typeAsked, subtitle, this)
        childFragmentManager
            .beginTransaction()
            .add(binding.mainSyncWithOtherSub.id, myFragment)
            .addToBackStack(null)
            .commit()
    }
    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = FragmentSyncWithOtherSubBinding.inflate(inflater, container, false)
        val view = binding.root
        updateText()
        binding.openLocalSub.setOnClickListener {
            val intent = Intent()
                .setType("*/*")
                .setAction(Intent.ACTION_GET_CONTENT)
            resultLauncher.launch(Intent.createChooser(intent, getString(R.string.select_a_file)))
        }
        binding.openLanSubtitle.setOnClickListener {
            val action = FragmentSyncWithOtherSubDirections
                .actionFragmentSyncWithOtherSubToFragmentSMBExplorer(
                    savingLocation = false,
                    listAllFiles = false,
                    extensions = arrayOf("srt", "sub"),
                    startLocation = "",
                    request = "OpenOtherSub"
                )
            view.findNavController().navigate(action)
        }
        binding.chooseFirstSelf.setOnClickListener {
            chooseSubtitle(ReceivedInfo.FIRST_SELF, currentSub.subtitle)
        }
        binding.chooseLastSelf.setOnClickListener {
            chooseSubtitle(ReceivedInfo.LAST_SELF, currentSub.subtitle)
        }
        binding.chooseLastOther.setOnClickListener {
            if (ReceivedInfo.otherSubInitialized) {
                chooseSubtitle(ReceivedInfo.LAST_OTHER, ReceivedInfo.otherSubtitle)
            } else {
                Toast.makeText(requireContext(), getString(R.string.first_select_a_subtitle), Toast.LENGTH_SHORT).show()
            }
        }
        currentSub.subtitle.subtitleChange.observe(viewLifecycleOwner, Observer {
            updateText()
            currentSub.backupSession(this, it)
        })
        binding.chooseFirstOther.setOnClickListener {
            if (ReceivedInfo.otherSubInitialized) {
                chooseSubtitle(ReceivedInfo.FIRST_OTHER, ReceivedInfo.otherSubtitle)
            } else {
                Toast.makeText(requireContext(), getString(R.string.first_select_a_subtitle), Toast.LENGTH_SHORT).show()
            }
        }
        binding.syncSubtitles.setOnClickListener {
            if (ReceivedInfo.otherSubInitialized) {
                val newStart =
                    ReceivedInfo.otherSubtitle.getLine(ReceivedInfo.getValue(ReceivedInfo.FIRST_OTHER)).startTime
                val newEnd =
                    ReceivedInfo.otherSubtitle.getLine(ReceivedInfo.getValue(ReceivedInfo.LAST_OTHER)).startTime
                currentSub.subtitle.syncSubtitlesToManualChoice(
                    newStart,
                    newEnd,
                    ReceivedInfo.getValue(ReceivedInfo.FIRST_SELF),
                    ReceivedInfo.getValue(ReceivedInfo.LAST_SELF)
                )
                updateText()
            }
        }
        toolbar = ToolBarHelper()
        toolbar.getToolbar(this)
        toolbar.hideItems(listOf(R.id.view_toolbar))
        toolbar.addDescription(getString(R.string.FragmentSyncWithOtherSub))
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