package com.subeditor.android_subtitle_editor

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import java.lang.NumberFormatException

class GenericMessageDialog : DialogFragment() {
    var message = ""
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        if (savedInstanceState != null) {
            message = savedInstanceState.getString("Message")!!
        }
        val dd = android.app.AlertDialog.Builder(requireContext())
            .setMessage(message)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->}
        return dd.show()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("Message", message)
        super.onSaveInstanceState(outState)
    }
}

class FrameRateDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dd = Dialog(requireContext())
        dd.setContentView(R.layout.frame_rate_dialog)
        dd.findViewById<TextView>(R.id.ok_button).setOnClickListener {
            val result = dd.findViewById<RadioGroup>(R.id.radio_frame_rate).checkedRadioButtonId
            val ee = dd.findViewById<RadioButton>(result)
            if (ee.text != "Custom") {
                val frameRate = ee.text.toString().toDouble()
                setFragmentResult("ChosenFrameRate", bundleOf(Pair("FrameRate", frameRate)))
                dismiss()
            } else {
                val found = dd.findViewById<EditText>(R.id.frame_rate_edit).text
                try {
                    val entered = found.toString().toDouble()
                    setFragmentResult("ChosenFrameRate", bundleOf(Pair("FrameRate", entered)))
                    dismiss()
                } catch (e: NumberFormatException) {
                    dismiss()
                }
            }
        }
        dd.findViewById<TextView>(R.id.question).setOnClickListener {
            val messageDialog = GenericMessageDialog()
            messageDialog.message = requireActivity().getString(R.string.sub_help)
            messageDialog.show(childFragmentManager, "")
        }
        return dd
    }
}

class NewFileChecking(private val requestCode: Int, val fragment: Fragment) {
    companion object {
        const val FOR_LOCAL_OPENING = 10
        const val FOR_LAN_OPENING = 11
        const val FOR_SYNC_OPENING_LOCAL = 12
        const val FOR_SYNC_OPENING_LAN = 13
        const val FOR_LOCAL_FILE_OPENING = 14
    }
    lateinit var subtitle: Subtitle
    lateinit var uri : Uri
    lateinit var filePath : String

    private fun returnToFragment() {
        val currentSub: CurrentSub by fragment.activityViewModels()
        when (requestCode) {
            FOR_LOCAL_OPENING -> {
                currentSub.addSubtitleLocation(uri, CurrentSub.URI, CurrentSub.LOCAL, filePath)
                currentSub.addSubtitle(subtitle)
                currentSub.backupSession(fragment, otherCall = true)
                (fragment as FragmentLoadFile).subtitleLoaded()
            }
            FOR_LOCAL_FILE_OPENING -> {
                currentSub.addSubtitleLocation(uri, CurrentSub.URI, CurrentSub.LOCAL, filePath)
                currentSub.addSubtitle(subtitle)
                currentSub.backupSession(fragment, otherCall = true)
                (fragment as FragmentLoadFile).subtitleLoaded()
            }
            FOR_LAN_OPENING -> {
                currentSub.addSubtitleLocation(filePath, CurrentSub.STRING, CurrentSub.LAN, filePath)
                currentSub.addSubtitle(subtitle)
                currentSub.backupSession(fragment, otherCall = true)
                (fragment as FragmentSMBExplorer).subtitleLoaded()
            }
            FOR_SYNC_OPENING_LAN -> {
                ReceivedInfo.loadOtherSubtitle(subtitle)
                (fragment as FragmentSMBExplorer).subtitleLoadedForSync()
            }
            FOR_SYNC_OPENING_LOCAL -> {
                ReceivedInfo.loadOtherSubtitle(subtitle)
                (fragment as FragmentSyncWithOtherSub).subtitleLoaded(subtitle)
            }
        }
    }
    private fun onBadSelection(msg: String?) {
        val messageDialog = GenericMessageDialog()
        messageDialog.message = msg!!
        messageDialog.show(fragment.childFragmentManager, "")
    }
    private fun askForFrameRateNew(fragment1: Fragment, results: ByteArray, filepath: String) {
        val frameRateDialog = FrameRateDialog()
        frameRateDialog.show(fragment1.childFragmentManager, "")
        fragment1.childFragmentManager.setFragmentResultListener("ChosenFrameRate", fragment)
        {_, bundle ->
                try {
                    val result = bundle.getDouble("FrameRate")
                    subtitle = SubtitleFactory(results, filepath).createSub(result)
                    returnToFragment()
                } catch (e : NoSubtitlesFoundException) {
                    onBadSelection(e.localizedMessage)
                }
            }
        }
    private fun checkIfPathOrFileIsValid(pathOrFile: String) : Boolean {
        return Subs.validSubtitleMap.containsKey(pathOrFile.substringAfterLast("."))
    }
    private fun checkNeeds(pathOrFile: String) : Int? {
        return Subs.additionalNeeds[pathOrFile.substringAfterLast(".")]
    }
    fun checkSequence(fragment: Fragment, filepath: String, results : ByteArray) {
        if (!checkIfPathOrFileIsValid(filepath)) {
            onBadSelection(fragment.getString(R.string.editor_only_works_with_sub_and_srt))
        } else if (checkNeeds(filepath) == Subs.NEEDS_FRAME_RATE) {
            askForFrameRateNew(fragment, results, filepath)
        } else {
            try {
                subtitle = SubtitleFactory(results, filepath).createSub()
                returnToFragment()
            } catch (e: Exception) {
                onBadSelection(e.localizedMessage)
            }
        }
    }
    fun unKnownExtension(fragment: Fragment, results: ByteArray) {
        try {
            subtitle = SubtitleFactory(results, "").createSub()
            returnToFragment()
        } catch (e: NoSubtitlesFoundException) {
            try {
                SubtitleFactory(results, "tryout.sub").createSub()
                askForFrameRateNew(fragment, results, "tryout.sub")
            } catch (e: NoSubtitlesFoundException) {
                onBadSelection(e.localizedMessage)
            }
        }
    }
}