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
import androidx.navigation.fragment.findNavController
import com.subeditor.android_subtitle_editor.databinding.FragmentLoadFileBinding

class FragmentLoadFile : Fragment() {
    private var _binding: FragmentLoadFileBinding? = null
    private val binding get() = _binding!!
    private val currentSub: CurrentSub by activityViewModels()
    private fun checkFileSize(selectedFile: Uri, fileSize: Long, sideLoaded: Boolean) {
        if (fileSize < Subs.IMPROBABLE_FILE_SIZE ) {
            if (sideLoaded) {
                loadSideLoadedFile(selectedFile)
            } else {
                afterSizeCheck(selectedFile)
            }
        } else {
            val askConfirmationFragment = AskConfirmationFragment()
            askConfirmationFragment.message = getString(R.string.ask_if_size_is_big)
            askConfirmationFragment.show(childFragmentManager, "")
            childFragmentManager.setFragmentResultListener("requestConfirm", this)
            { _, bundle ->
                val resultReceived = bundle.getBoolean("Confirmed")
                if (resultReceived && !sideLoaded) {
                    afterSizeCheck(selectedFile)
                } else if (resultReceived && sideLoaded) {
                    loadSideLoadedFile(selectedFile)
                }
            }
        }
    }
    private fun loadSideLoadedFile(selectedFile: Uri) {
        currentSub.loadedFromFileExplorer = true
        val results = context?.contentResolver?.openInputStream(selectedFile)!!.readBytes()
        val fileCheck = NewFileChecking(NewFileChecking.FOR_LOCAL_FILE_OPENING, this)
        fileCheck.uri = selectedFile
        val dd = FileUtils(requireActivity()).getPath(selectedFile)
        if (dd != null) {
            fileCheck.filePath = dd.toString()
            fileCheck.checkSequence(this, dd.toString(), results)
        } else {
            fileCheck.filePath = "Unknown"
            fileCheck.unKnownExtension(this, results)
        }
    }

    private fun loadSubtitle(results: ByteArray, filepath: String, selectedFile: Uri) {
        val fileCheck = NewFileChecking(NewFileChecking.FOR_LOCAL_OPENING, this)
        fileCheck.uri = selectedFile
        fileCheck.filePath = filepath
        fileCheck.checkSequence(this, filepath, results)
    }
    fun subtitleLoaded() {
        findNavController().navigate(R.id.fragmentMainMenu)
    }
    private fun afterSizeCheck(selectedFile: Uri) {
        try {
            val dd = FileUtils(requireContext()).getPath(selectedFile)
            requireContext().contentResolver?.takePersistableUriPermission(selectedFile, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val results = context?.contentResolver?.openInputStream(selectedFile)!!.readBytes()
            loadSubtitle(results, dd!!, selectedFile)
        } catch (e: Exception) {
            Toast.makeText(context, getString(R.string.a_error_has_occurred), Toast.LENGTH_LONG).show()
        }
    }
    private var resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK && result.data?.data is Uri) {
            try {
                val selectedFile = result.data?.data
                val size = context?.contentResolver?.openAssetFileDescriptor(selectedFile!!, "r")?.length
                checkFileSize(selectedFile!!, size!!, false)
            } catch (e: Exception) {
                Toast.makeText(context, getString(R.string.a_error_has_occurred), Toast.LENGTH_LONG).show()
            }
        }
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        ReceivedInfo.currentSub = currentSub
        _binding = FragmentLoadFileBinding.inflate(inflater, container, false)
        val view = binding.root
        try {
            val action = FragmentLoadFileArgs.fromBundle(requireArguments()).action
            if (action == "loadFromFile") {
                if (activity?.intent?.data != null) {
                    val data = activity?.intent?.data
                    val size = context?.contentResolver?.openAssetFileDescriptor(data!!, "r")?.length
                    checkFileSize(data!!, size!!, true)
                }
            } else if (action == "externalLoadFromFile") {
                val size = context?.contentResolver?.openAssetFileDescriptor(currentSub.newUriIntent!!, "r")?.length
                checkFileSize(currentSub.newUriIntent!!, size!!, true)
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.local_open_file_failed), Toast.LENGTH_LONG).show()
        }
        binding.openFile.setOnClickListener {
            val intent = Intent()
                .setType("*/*")
                .setAction(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            resultLauncher.launch(Intent.createChooser(intent, getString(R.string.select_a_file)))

        }
        binding.networkSrtFile.setOnClickListener {
            val lanAction = FragmentLoadFileDirections.actionFragmentLoadFileToFragmentSMBExplorer(
                savingLocation = false,
                listAllFiles = false,
                extensions = arrayOf("srt", "sub"),
                startLocation = "", request = "OpenSelfSub"
            )
            findNavController().navigate(lanAction)
        }
        if (currentSub.checkBackup(this)) { binding.restoreSession.visibility = View.VISIBLE }
        binding.restoreSession.setOnClickListener {
            currentSub.restoreSession(this)
            val restoreAction = FragmentLoadFileDirections.actionFragmentLoadFileToFragmentMainMenu()
            findNavController().navigate(restoreAction)
        }

        return view
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}