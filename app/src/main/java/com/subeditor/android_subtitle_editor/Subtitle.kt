package com.subeditor.android_subtitle_editor

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.Serializable


@kotlinx.serialization.Serializable
class Subtitle(var subtitleLines: ArrayList<SubtitleLine>) : Serializable {
    @kotlinx.serialization.Transient var subtitleChangeMutable = MutableLiveData<SubtitleChange?>(null)
    val subtitleChange : LiveData<SubtitleChange?> get() = subtitleChangeMutable
    var fileType: Int = 0
    private var startingTime:Int = 0
    private var endingTime:Int = 0
    var length: Int = 0
    var frameRate : Double = 0.0
    var contentsOfFileAsByteArray : ByteArray? = null
    var usedCharSet : String = "UTF-8"
    var actions = arrayListOf<SubAction>()
    private var correctedAction = false
    var undoneActions = arrayListOf<SubAction>()
    var redoPossible = false
    var noCharsetDetected = false
    init {
        getFactors()
    }
    fun resetListeners(){
        subtitleChangeMutable = MutableLiveData<SubtitleChange?>(null)
    }
    private fun recordChanges(subtitleChange: SubtitleChange) {
        subtitleChangeMutable.value = subtitleChange
    }
    fun findIndexOfIndividualSubtitle(subtitleLineToFind: SubtitleLine?): Int {
        subtitleLines.forEachIndexed{ index, subtitleLine ->
            if (subtitleLine == subtitleLineToFind) {
                return index
            }
        }
        return -1
    }
    fun findSubtitleLineById(id: Int) : Int {
        subtitleLines.forEachIndexed { index, subtitleLine ->
            if (subtitleLine.id == id) { return index }
        }
        return -1
    }
    fun getLine(index: Int): SubtitleLine {
        return subtitleLines[index]
    }
    operator fun get (index: Int): String {
        return (index+1).toString() + "\n" + subtitleLines[index].toString()
    }
    private fun getFactors () {
        startingTime = subtitleLines.first().startTime
        endingTime = subtitleLines.last().startTime
        length = endingTime - startingTime
    }
    fun removeAt(index: Int) {
        addAction(DeletionAction(index, this.getLine(index)))
        subtitleLines.removeAt(index)
        getFactors()
        recordChanges(SubtitleChange(SubtitleChange.SINGLE, index, SubtitleChange.DELETION))
    }
    fun removeAtBySwipe(index: Int) {
        addAction(SwipeDeletionAction(index, this.getLine(index)))
        subtitleLines.removeAt(index)
        getFactors()
        recordChanges(SubtitleChange(SubtitleChange.SINGLE, index, SubtitleChange.DELETION))
    }
    private fun actualShift(byMilliseconds: Int) {
        this.subtitleLines.forEach {
            it.startTime += byMilliseconds
            it.endTime += byMilliseconds
        }
        getFactors()
        recordChanges(SubtitleChange(SubtitleChange.COMPLETE))
    }
    fun shift(byMilliseconds: Int){
        addAction(ShiftAction(byMilliseconds))
        actualShift(byMilliseconds)
    }
    fun stretch(factor: Double){
        addAction(StretchAction(factor))
        actualStretch(factor, false)
    }
    fun saveTempFile(context: Context): String {
        val outputFile = File(context.cacheDir, "subtitle_temp_file.nl.srt")
        val input = this.toString()
        val inputStream = ByteArrayInputStream(input.toByteArray(Charsets.UTF_8))
        val outputStream = FileOutputStream(outputFile)
        inputStream.copyTo(outputStream)
        return outputFile.absolutePath
    }
    private fun actualStretch(factor: Double, correct:Boolean = true) {
        var correction = factor * 1 - 1
        if (!correct) {
            correction = 0.toDouble()
        }
        this.subtitleLines.forEach {
            it.startTime = (it.startTime * factor).toInt()
            it.endTime = (it.endTime * factor).toInt()
            it.startTime = (it.startTime - correction).toInt()
            it.endTime = (it.endTime - correction).toInt()
        }
        getFactors()
        recordChanges(SubtitleChange(SubtitleChange.COMPLETE, 0, 0))
    }
    fun syncSubtitlesToManualChoice(newStartTime: Int, newEndTime: Int, StartIndexOfSub:Int = 0, EndIndexOfSub: Int = subtitleLines.lastIndex) {
        val oldStart    = subtitleLines[StartIndexOfSub].startTime
        val oldEnd = subtitleLines[EndIndexOfSub].startTime
        addAction(SyncByManualChoiceAction(newStartTime, newEndTime, StartIndexOfSub, EndIndexOfSub, oldStart, oldEnd))
        val oldLength = oldEnd - oldStart
        val newLength = newEndTime - newStartTime
        val newFactor : Double = newLength.toDouble() / oldLength.toDouble()
        actualStretch(newFactor)
        val newShift = newStartTime - subtitleLines[StartIndexOfSub].startTime
        actualShift(newShift)
    }
    fun giveNewTimes(startTime: Int = startingTime, endTime: Int = endingTime) {
        addAction(GiveNewTimesAction(startTime, endTime, startingTime, endingTime))
        val newLength = endTime.toDouble() - startTime.toDouble()
        val newFactor : Double = newLength / length
        actualStretch(newFactor)
        val newShift = startTime - startingTime
        actualShift(newShift)
    }
    fun giveNewStartTimeInSeparateTimeUnits(newHours: Int, newMinutes: Int, newSeconds: Int, newMilliseconds: Int) {
        val newStartTime = newHours * 3600000 + newMinutes * 60000 + newSeconds * 1000 + newMilliseconds
        giveNewStartTime(newStartTime)
    }
    fun giveNewEndTimeInSeparateTimeUnits(newHours: Int, newMinutes: Int, newSeconds: Int, newMilliseconds: Int) {
        val newEndTime = newHours * 3600000 + newMinutes * 60000 + newSeconds * 1000 + newMilliseconds
        giveNewEndTime(newEndTime)
    }
    private fun giveNewStartTime(newStartTime: Int) {
        giveNewTimes(newStartTime, this.endingTime)
    }
    private fun giveNewEndTime(newEndTime: Int) {
        giveNewTimes(this.startingTime, newEndTime)
    }
    fun insertSubtitleLine(index: Int, subtitleLine: SubtitleLine) {
        subtitleLines.add(index, subtitleLine)
        getFactors()
        recordChanges(SubtitleChange(SubtitleChange.SINGLE, index, SubtitleChange.INSERTION))
    }
    fun size(): Int {
        return subtitleLines.size
    }
    fun getJustTheText(index: Int): String {
        return subtitleLines[index].getJustTheText()
    }
    fun changeLengthOfIndividualSubtitle(index: Int, extraLength: Int) : Boolean {
        val max = if (index < subtitleLines.lastIndex) subtitleLines[index+1].startTime else Int.MAX_VALUE
        val min = subtitleLines[index].endTime + extraLength
        if (subtitleLines[index].endTime + extraLength < max && min > subtitleLines[index].startTime) {
            addAction(ChangeLengthAction(index, extraLength))
            subtitleLines[index].endTime += extraLength
            getFactors()
            recordChanges(SubtitleChange(SubtitleChange.SINGLE, index, SubtitleChange.CHANGE))
            return true
        }
        return false
    }
    fun shiftIndividualSubtitle(index: Int, shift: Int): Boolean {
        val min = if (index > 0) subtitleLines[index-1].endTime else 0
        val max = if (index < subtitleLines.lastIndex) subtitleLines[index+1].startTime else Int.MAX_VALUE
        if (subtitleLines[index].startTime + shift > min && subtitleLines[index].endTime + shift < max) {
            addAction(ShiftIndividualAction(index, shift))
            subtitleLines[index].startTime += shift
            subtitleLines[index].endTime += shift
            getFactors()
            recordChanges(SubtitleChange(SubtitleChange.SINGLE, index, SubtitleChange.CHANGE))
            return true
        }
        return false
    }
    fun getStartTimeAsString(index: Int): String {
        val start = subtitleLines[index].startTime
        return subtitleLines[index].recreateLine(start)
    }
    fun getEndTimeAsString(index: Int): String {
        val end = subtitleLines[index].endTime
        return subtitleLines[index].recreateLine(end)
    }
    fun insertTextIntoSubtitle(index: Int, newText: String) {
        addAction(InsertionAction(index, this.getJustTheText(index), newText))
        val arrayText = ArrayList(newText.split("\n"))
        subtitleLines[index].allStrings = arrayText
        recordChanges(SubtitleChange(SubtitleChange.SINGLE, index, SubtitleChange.CHANGE))
    }
    private fun addAction(action: SubAction) {
        if (!correctedAction) {
            actions.add(action)
            undoneActions.clear()
            redoPossible = false
        }
    }
    fun undoLastAction() {
        if (actions.isNotEmpty()) {
            correctedAction = true
            actions[actions.lastIndex].undoAction(this)
            undoneActions.add(actions[actions.lastIndex])
            actions.removeAt(actions.lastIndex)
            redoPossible = true
            correctedAction = false
            getFactors()
        }

    }
    fun redoLastAction() {
        if (undoneActions.isNotEmpty()) {
            correctedAction = true
            undoneActions[undoneActions.lastIndex].redoAction(this)
            actions.add(undoneActions[undoneActions.lastIndex])
            undoneActions.removeAt(undoneActions.lastIndex)
            if (undoneActions.isEmpty()) {
                redoPossible = false
            }
            correctedAction = false
            getFactors()
        }

    }
    fun restoreMarkup(newSubtitleLines: ArrayList<ArrayList<String>>) {
        subtitleLines.forEachIndexed{ index, subtitleLine ->
            subtitleLine.allStrings = newSubtitleLines[index]
        }
        recordChanges(SubtitleChange(SubtitleChange.COMPLETE, 0, 0))
    }
    fun deleteAllMarkUp() {
        val oldSubtitleLines = arrayListOf<ArrayList<String>>()
        val newSubtitleLines = arrayListOf<ArrayList<String>>()
        subtitleLines.forEachIndexed { _, subtitleLine ->
            oldSubtitleLines.add(subtitleLine.allStrings)
            val newText = subtitleLine.toSpannableJustTheText().toString()
            subtitleLine.allStrings = newText.split("\n") as ArrayList<String>
            newSubtitleLines.add(subtitleLine.allStrings)
        }
        addAction(DeleteAllMarkupAction(oldSubtitleLines, newSubtitleLines))
        recordChanges(SubtitleChange(SubtitleChange.COMPLETE, 0, 0))
    }
    fun provideNewMarkUp(bold: Boolean, italic: Boolean, underLine: Boolean, color: String="") {
        val oldSubtitleLines = arrayListOf<ArrayList<String>>()
        val newSubtitleLines = arrayListOf<ArrayList<String>>()
        subtitleLines.forEachIndexed { _, subtitleLine ->
            oldSubtitleLines.add(subtitleLine.allStrings)
            val newText = subtitleLine.toSpannableJustTheText().toString()
            subtitleLine.allStrings = newText.split("\n") as ArrayList<String>
            val newString = arrayListOf<String>()
            subtitleLine.allStrings.forEach{
                var currentString = it
                if (currentString.isNotEmpty()) {
                    if (bold)        { currentString = "<b>$currentString</b>" }
                    if (italic)      { currentString = "<i>$currentString</i>" }
                    if (underLine)   { currentString = "<u>$currentString</u>" }
                    if (color != "") { currentString = """<font color="$color">$currentString</font>""" }
                    newString.add(currentString.trim())
                }
            }
            subtitleLine.allStrings = newString
            newSubtitleLines.add(newString)
        }
        addAction(DeleteAllMarkupAction(oldSubtitleLines, newSubtitleLines))
        recordChanges(SubtitleChange(SubtitleChange.COMPLETE, 0, 0))
    }
    fun addSubtitleLine(index: Int, text: String): Boolean {
        val min = subtitleLines[index].endTime + 100
        val max = if (index < subtitleLines.lastIndex) subtitleLines[index+1].startTime -100 else min + 2000
        val end = if ((max - min) > 2000) 2000 else (max - min)
        if (end > 100) {
            val newSubtitleLine = SubtitleLine(10000, min, min+end, end,
                ArrayList(text.split("\n")))
            addAction(InsertionNewLineAction(index+1, newSubtitleLine))
            subtitleLines.add(index+1, newSubtitleLine)
            recordChanges(SubtitleChange(SubtitleChange.SINGLE, index, SubtitleChange.INSERTION))
            return true
        }
        return false
    }
    fun getCurrentResultingFile(): ArrayList<String> {
        val currentResultingFile = arrayListOf<String>()
        subtitleLines.forEachIndexed {index, element ->
            val singleSub = "${index + 1}\n" + "${element}\n"
            singleSub.split("\n").forEach {
                currentResultingFile.add(it)
            }
        }
        return currentResultingFile
    }
    override fun toString(): String {
        var all = ""
        subtitleLines.forEachIndexed {index, element ->
            all += "${index + 1}\n" + "${element}\n\n"
        }
        return all
    }
}