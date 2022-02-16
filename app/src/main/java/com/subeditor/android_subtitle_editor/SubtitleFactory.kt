package com.subeditor.android_subtitle_editor

import org.mozilla.intl.chardet.nsDetector

class SubtitleFactory(private val fileAsByteArray: ByteArray, filename: String) {
    private var fileType = 1
    private var fileAsString = ""
    private var chosenCharset = "utf-8"
    private var noCharsetDetected = false
    init {
        fileType = Subs.SRT
        if (filename.endsWith(".sub")) {
            fileType = Subs.SUB
        }
    }
    private fun getCharset() {
        val detector = nsDetector()
        detector.HandleData(fileAsByteArray, fileAsByteArray.size)
        val ddd = detector.probableCharsets
        chosenCharset = ddd[0]
        if (chosenCharset == "nomatch") {
            chosenCharset = "utf-8"
            noCharsetDetected = true
        }
        val charset1 = charset(chosenCharset)
        fileAsString = String(fileAsByteArray, charset1)
    }
    fun createSub(frameRate: Double=25.0): Subtitle {
        getCharset()
        if (fileType == Subs.SRT) {
            val subtitle =  SrtSubtitleCreator(fileAsString).build()
            subtitle.fileType = Subs.SRT
            subtitle.noCharsetDetected = noCharsetDetected
            subtitle.usedCharSet = chosenCharset
            subtitle.contentsOfFileAsByteArray = fileAsByteArray
            return subtitle
        } else if (fileType == Subs.SUB) {
            val subtitle = SubSubtitleCreator(fileAsString, frameRate).build()
            subtitle.fileType = Subs.SUB
            subtitle.frameRate = frameRate
            subtitle.noCharsetDetected = noCharsetDetected
            subtitle.usedCharSet = chosenCharset
            subtitle.contentsOfFileAsByteArray = fileAsByteArray
            return subtitle
        }
        throw Exception("An unknown error occurred")
    }
}