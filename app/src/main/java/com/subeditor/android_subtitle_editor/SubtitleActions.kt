package com.subeditor.android_subtitle_editor

import android.content.Context
import android.text.Spanned
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.Serializable
import com.subeditor.android_subtitle_editor.SubAction as SubAction

@kotlinx.serialization.Serializable
sealed class SubAction : Serializable {
    abstract fun undoAction(subtitle: Subtitle)
    abstract fun redoAction(subtitle: Subtitle)
}

@kotlinx.serialization.Serializable
class DeletionAction(private val index: Int, private val subtitleLine: SubtitleLine) : SubAction() , Serializable{
    override fun undoAction(subtitle: Subtitle) {
        subtitle.insertSubtitleLine(index, subtitleLine)
    }
    override fun redoAction(subtitle: Subtitle) {
        subtitle.removeAt(index)
    }
    override fun toString(): String {
        return """DELETION: ${index}, ${subtitleLine}, """
    }
}
@kotlinx.serialization.Serializable
class SwipeDeletionAction(private val index: Int, private val subtitleLine: SubtitleLine) : SubAction(), Serializable {
    override fun undoAction(subtitle: Subtitle) {
        subtitle.insertSubtitleLine(index, subtitleLine)
    }
    override fun redoAction(subtitle: Subtitle) {
        subtitle.removeAt(index)
    }
    override fun toString(): String {
        return """SWIPE DELETION: ${index}, ${subtitleLine}, """
    }
}
@kotlinx.serialization.Serializable
class InsertionNewLineAction(private val index: Int, private val subtitleLine: SubtitleLine) : SubAction() , Serializable{
    override fun undoAction(subtitle: Subtitle) {
        subtitle.removeAt(index)
    }
    override fun redoAction(subtitle: Subtitle) {
        subtitle.insertSubtitleLine(index, subtitleLine)
    }
    override fun toString(): String {
        return """INSERTION NEW SUBTITLE LINE: ${index}, """
    }
}
@kotlinx.serialization.Serializable
class InsertionAction(private val index: Int, private val oldText: String, private val newText: String) : SubAction() , Serializable{
    override fun undoAction(subtitle: Subtitle) {
        subtitle.insertTextIntoSubtitle(index, oldText)
    }
    override fun redoAction(subtitle: Subtitle) {
        subtitle.insertTextIntoSubtitle(index, newText)
    }
    override fun toString(): String {
        return """INSERTION: ${index}, ${oldText}, $newText"""
    }
}
@kotlinx.serialization.Serializable
class ShiftAction(private val shift: Int): SubAction(), Serializable {
    override fun undoAction(subtitle: Subtitle) {
        subtitle.shift(shift*-1)
    }
    override fun redoAction(subtitle: Subtitle) {
        subtitle.shift(shift)
    }
    override fun toString(): String {
        return """SHIFT: ${shift}, ${shift*-1}, """
    }
}
@kotlinx.serialization.Serializable
class ChangeLengthAction(private val index: Int, private val extraLength: Int): SubAction(), Serializable {
    override fun undoAction(subtitle: Subtitle) {
        subtitle.changeLengthOfIndividualSubtitle(index, extraLength*-1)
    }
    override fun redoAction(subtitle: Subtitle) {
        subtitle.changeLengthOfIndividualSubtitle(index, extraLength)
    }
    override fun toString(): String {
        return """SUBTITLE LINE AT: ${index}, HAS LENGTH CHANGED BY ${extraLength}, """
    }
}
@kotlinx.serialization.Serializable
class ShiftIndividualAction(private val index: Int, private val shift: Int): SubAction(), Serializable {
    override fun undoAction(subtitle: Subtitle) {
        subtitle.shiftIndividualSubtitle(index, shift*-1)
    }
    override fun redoAction(subtitle: Subtitle) {
        subtitle.shiftIndividualSubtitle(index, shift)
    }
    override fun toString(): String {
        return """SUBTITLE LINE AT: ${index}, HAS SHIFTED BY ${shift}, """
    }
}
@kotlinx.serialization.Serializable
class StretchAction(private val factor: Double) : SubAction(), Serializable{
    override fun undoAction(subtitle: Subtitle) {
        val newFactor = 1 / factor
        subtitle.stretch(newFactor)
    }
    override fun redoAction(subtitle: Subtitle) {
        subtitle.stretch(factor)
    }
    override fun toString(): String {
        return """STRETCH: ${factor}, ${1/factor}, $"""
    }
}
@kotlinx.serialization.Serializable
class GiveNewTimesAction(private val NewStartTime: Int,
                         private val NewEndTime: Int,
                         private val OldStartTime: Int,
                         private val OldEndTime: Int): SubAction(), Serializable {
    override fun undoAction(subtitle: Subtitle) {
        subtitle.giveNewTimes(OldStartTime, OldEndTime)
    }
    override fun redoAction(subtitle: Subtitle) {
        subtitle.giveNewTimes(NewStartTime, NewEndTime)
    }
    override fun toString(): String {
        return """GIVE NEW TIMES: ${NewStartTime}, ${NewEndTime}, ${OldStartTime}, $OldEndTime $"""
    }
}
@kotlinx.serialization.Serializable
class SyncByManualChoiceAction(
    private val NewStartTime: Int,
    private val NewEndTime: Int,
    private val StartIndexOfSub: Int,
    private val EndIndexOfSub: Int,
    private val oldStart: Int,
    private val oldEnd: Int) : SubAction(), Serializable {
    override fun undoAction(subtitle: Subtitle) {
        subtitle.syncSubtitlesToManualChoice(oldStart, oldEnd, StartIndexOfSub, EndIndexOfSub)
    }
    override fun redoAction(subtitle: Subtitle) {
        subtitle.syncSubtitlesToManualChoice(NewStartTime,NewEndTime, StartIndexOfSub,EndIndexOfSub)
    }
    override fun toString(): String {
        return """SYNC BY MANUAL CHOICE: ${NewStartTime}, ${NewEndTime}, ${oldStart}, $oldEnd $StartIndexOfSub $EndIndexOfSub $"""
    }
}
@kotlinx.serialization.Serializable
class DeleteAllMarkupAction(private val oldSubtitleLines: ArrayList<ArrayList<String>>,
                            private val newSubtitleLines: ArrayList<ArrayList<String>>) : SubAction(), Serializable{
    override fun undoAction(subtitle: Subtitle) {
        subtitle.restoreMarkup(oldSubtitleLines)
    }
    override fun redoAction(subtitle: Subtitle) {
        subtitle.restoreMarkup(newSubtitleLines)
    }
    override fun toString(): String {
        return "DELETE ALL MARKUP"
    }
}