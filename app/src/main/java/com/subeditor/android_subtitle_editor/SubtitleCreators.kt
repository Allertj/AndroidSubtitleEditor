package com.subeditor.android_subtitle_editor

import org.mozilla.intl.chardet.nsDetector

class NoSubtitlesFoundException(override val message: String?) : Exception()

object Subs {
    const val IMPROBABLE_FILE_SIZE = 1048576
    const val HOURS = 2
    const val MINUTES = 3
    const val SECONDS = 4
    const val MILLISECONDS = 5

    const val SRT = 10
    const val SUB = 11

    private const val NONE = 0
    const val NEEDS_FRAME_RATE = 2

    val validSubtitleMap = mapOf("srt" to SRT, "sub" to SUB)
    val additionalNeeds = mapOf(".srt" to NONE, "sub" to NEEDS_FRAME_RATE)
    val videoExtensions = arrayOf("mp4", "mkv", "mov")
}

abstract class SubtitleCreator {
    abstract var subtitleLines : ArrayList<SubtitleLine>
    abstract fun preliminaryTest(arrayOfLoadedString: ArrayList<String>): Boolean
    abstract fun loadSubtitle(arrayOfLoadedString: ArrayList<String>)
    abstract fun createSubtitleLine(id: Int, startTime: Int, endTime: Int, text: List<String>)
    abstract fun build(): Subtitle
}

class SubSubtitleCreator(private val fileAsString: String, private val frameRate: Double): SubtitleCreator() {
    override var subtitleLines : ArrayList<SubtitleLine> = arrayListOf()
    override fun preliminaryTest(arrayOfLoadedString: ArrayList<String>): Boolean {
        arrayOfLoadedString.forEach {
            val subRegex = """^\{\d+\}\{\d+\}.+""".toRegex()
            if (subRegex.find(it) != null) {
                return true
            }
        }
        return false
    }
    override fun loadSubtitle(arrayOfLoadedString: ArrayList<String>) {
        var index = 0
        arrayOfLoadedString.forEach {
            if (it.startsWith("{")) {
                val startLineIndex = it.indexOf("{")
                val midlineIndex = it.indexOf("}{")
                val endLineIndex = it.indexOf("}", midlineIndex + 1)
                val startTime = it.substring(startLineIndex + 1, midlineIndex)
                val endTime = it.substring(midlineIndex + 2, endLineIndex)
                val texts = it.substring(endLineIndex + 1, it.length)
                val text = texts.split("|")
                val startingTime = startTime.toDouble() / frameRate * 1000
                val endingTime = endTime.toDouble() / frameRate * 1000
                createSubtitleLine(index, startingTime.toInt(), endingTime.toInt(), text)
                index += 1
            }
        }
    }
    override fun createSubtitleLine(id: Int, startTime: Int, endTime: Int, text: List<String>) {
        val newText = arrayListOf<String>()
        text.forEach {
            newText.add(it)
        }
        val newline = SubtitleLine(id, startTime, endTime, endTime-startTime, newText)
        subtitleLines.add(newline)
    }

    override fun build(): Subtitle {
        val arrayOfLoadedString = (ArrayList(fileAsString.split("\n")) + arrayListOf("", "")) as ArrayList<String>
        if (preliminaryTest(arrayOfLoadedString)) {
            loadSubtitle(arrayOfLoadedString)
            return Subtitle(subtitleLines)
        }
        throw NoSubtitlesFoundException("No subtitles were found in this file")
    }
}
class SrtSubtitleCreator(private val fileAsString: String): SubtitleCreator() {
    override var subtitleLines: ArrayList<SubtitleLine> = arrayListOf()
    override fun preliminaryTest(arrayOfLoadedString: ArrayList<String>): Boolean {
        arrayOfLoadedString.forEach {
            val srtRegex = """\d\d:\d\d:\d\d,\d\d\d --> \d\d:\d\d:\d\d,\d\d\d""".toRegex()
            if (srtRegex.find(it) != null) {
                return true
            }
        }
        return false
    }
    private fun transform(timeline:String) :Int {
        val hours = timeline.substring(0, 2).toInt() * 3600000
        val minutes = timeline.substring(3, 5).toInt() * 60000
        val seconds = timeline.substring(6, 8).toInt() * 1000
        val milliseconds = timeline.substring(9, 12).toInt()
        return hours + minutes + seconds + milliseconds
    }
    private fun scanForEnding(rawSub: ArrayList<String>, index: Int): ArrayList<String> {
        val allStrings = ArrayList<String>()
        allStrings.add(rawSub[index+1].trim())
        val secondLine = rawSub[index+2].trim()
        if (secondLine.isNotEmpty()) {
            allStrings.add(secondLine)
        }
        return allStrings
    }
    override fun loadSubtitle(arrayOfLoadedString: ArrayList<String>) {
        arrayOfLoadedString.forEachIndexed {index, element ->
            if ((element.trim().length == 29 || element.length == 29) && (element.substring(13, 16) == "-->")) {
                val startTime = transform(element.substring(0, 12))
                val endTime = transform(element.substring(17, 29))
                val textLines = this.scanForEnding(arrayOfLoadedString, index)
                createSubtitleLine(index, startTime, endTime, textLines)
            }
        }
    }
    override fun createSubtitleLine(id: Int, startTime: Int, endTime: Int, text: List<String>) {
        val newline = SubtitleLine(id, startTime, endTime, endTime-startTime, text as ArrayList<String>)
        subtitleLines.add(newline)
    }
    override fun build(): Subtitle {
        val arrayOfLoadedString = (ArrayList(fileAsString.split("\n")) + arrayListOf("", "")) as ArrayList<String>
        if (preliminaryTest(arrayOfLoadedString)) {
            loadSubtitle(arrayOfLoadedString)
            return Subtitle(subtitleLines)
        }
        throw NoSubtitlesFoundException("No Subtitles were found in this File")
    }
}