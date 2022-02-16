package com.subeditor.android_subtitle_editor

import android.app.Dialog
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
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import com.google.android.gms.ads.AdRequest
import com.subeditor.android_subtitle_editor.databinding.FragmentSaveSubtitleBinding

class AskForVerificationDialog: DialogFragment(){
    var pathString = ""
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        if (savedInstanceState != null) { pathString = savedInstanceState.getString("pathString")!! }
            val dd = android.app.AlertDialog.Builder(requireContext())
                .setMessage(getString(R.string.overwrite) + " $pathString?")
                .setPositiveButton(getString(R.string.ok)) { _, _ ->
                    setFragmentResult("OverwriteResult", bundleOf(Pair("Overwrite", true)))
                }
                .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                    setFragmentResult("OverwriteResult", bundleOf(Pair("Overwrite", false)))
                }
            return dd.show()
        }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pathString", pathString)
        super.onSaveInstanceState(outState)
    }
}


class FragmentSaveSubtitle : Fragment() {
    private var _binding: FragmentSaveSubtitleBinding? = null
    private val binding get() = _binding!!
    private val currentSub: CurrentSub by activityViewModels()
    lateinit var toolbar: ToolBarHelper
    private var resultLauncherForLocal = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            val filePath = FileUtils(requireContext()).getPath(result.data?.data!!) as String
            currentSub.addSubtitleLocation(result.data?.data!!, CurrentSub.URI, CurrentSub.LOCAL, filePath)
            saveFileLocally(result.data?.data!!)
        }
    }

    private fun onResultOfVerification() {
        if (currentSub.subtitleLocatedAt == CurrentSub.LOCAL) {
            saveFileLocally(currentSub.subtitleLocation as Uri)
        } else if (currentSub.subtitleLocatedAt == CurrentSub.LAN) {
            KnownServers.saveStringToServerSimple(currentSub.subtitle.toString(), currentSub.subtitleLocation.toString(), this)

        }
    }
    fun onResultOfLanSaving(){
        requireActivity().runOnUiThread {
            try {
                 Toast.makeText(requireContext(), getString(R.string.file_successfully_saved), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this.requireContext(), getString(R.string.file_not_saved), Toast.LENGTH_LONG).show()
            }
        }
    }
    private fun saveFileLocally(uri1: Uri) {
        try {
            requireContext().contentResolver.openOutputStream(uri1)?.write(currentSub.subtitle.toString().toByteArray())
            currentSub.backupSession(this, otherCall = true)
            Toast.makeText(requireContext(), getString(R.string.file_successfully_saved), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this.requireContext(), getString(R.string.file_not_saved), Toast.LENGTH_LONG).show()
        }
    }
    private fun askForVerification(pathString : String ="") {
        val askForVerificationDialog = AskForVerificationDialog()
        askForVerificationDialog.pathString = pathString
        askForVerificationDialog.show(childFragmentManager, "")
        childFragmentManager.setFragmentResultListener("OverwriteResult", this)
        { _, bundle ->
            val overwrite = bundle.getBoolean("Overwrite")
            if (overwrite) { onResultOfVerification() }
        }
    }
    private fun loadSubAndVideo() {
        binding.currentSubtitle.text = currentSub.actualSubtitleLocation
        if (currentSub.actualVideoLocation.isNotEmpty()) {
            binding.currentVideo.text = currentSub.actualVideoLocation
        }
    }
    private fun selectLocalCustomDirectory() {
        val originalFilename = currentSub.subFileName.substringBeforeLast(".") + ".edited.srt"
        val intent = Intent()
        intent.action = Intent.ACTION_CREATE_DOCUMENT
        intent.type = "*/*"
        if (currentSub.subtitleLocatedAt == CurrentSub.LOCAL) {
            intent.putExtra("android.provider.extra.INITIAL_URI", currentSub.subtitleLocation.toString())
        }
        intent.putExtra(Intent.EXTRA_TITLE, originalFilename)
        resultLauncherForLocal.launch(intent)

    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSaveSubtitleBinding.inflate(inflater, container, false)
        val view = binding.root
        loadSubAndVideo()
        toolbar = ToolBarHelper()
        toolbar.getToolbar(this)
        toolbar.hideItems(listOf(R.id.help_toolbar, R.id.save_toolbar, R.id.undo_toolbar, R.id.redo_toolbar))
        if (currentSub.actualSubtitleLocation == "Unknown" ||currentSub.subtitle.fileType == Subs.SUB) {
            binding.overwriteCurrent.visibility = View.GONE
        }
        binding.overwriteCurrent.setOnClickListener {
            askForVerification(currentSub.actualSubtitleLocation)
        }
        binding.selectLocalDirectory.setOnClickListener {
            selectLocalCustomDirectory()
        }

        binding.selectLanDirectory.setOnClickListener {
            try {
                var startLocation = ""
                if (currentSub.subtitleLocatedAt == CurrentSub.LAN) {
                    startLocation =
                        currentSub.subtitleLocation.toString().substringBeforeLast("/") + "/"
                }
                val action =
                    FragmentSaveSubtitleDirections.actionFragmentSaveSubtitleToFragmentSMBExplorer(
                        savingLocation = true,
                        listAllFiles = false,
                        extensions = arrayOf("srt", "sub"),
                        startLocation = startLocation, request = "SaveSMBSubtitle",
                        showToolbar = true
                    )
                findNavController().navigate(action)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.localizedMessage, Toast.LENGTH_LONG).show()
            }
        }
        val adRequest = AdRequest.Builder().build()
        binding.adViewSave.loadAd(adRequest)
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