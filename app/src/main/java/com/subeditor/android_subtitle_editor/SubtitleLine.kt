package com.subeditor.android_subtitle_editor

import android.text.Spanned
import androidx.core.text.HtmlCompat
import java.io.Serializable

@kotlinx.serialization.Serializable
data class SubtitleLine(var id: Int, var startTime: Int, var endTime: Int, var length: Int, var allStrings: ArrayList<String>) :
    Serializable {
    fun recreateLine(decimal: Int) : String {
        val time = makeMapOfTimes(decimal)
        return "${time[Subs.HOURS]}:${time[Subs.MINUTES]}:${time[Subs.SECONDS]},${time[Subs.MILLISECONDS]}"
    }
    fun makeMapOfTimes(decimal : Int = startTime): Map<Int, String> {
        val hours = (decimal / 3600000).toString().padStart(2, '0')
        val restMinutes = decimal % 3600000
        val minutes = (restMinutes / 60000).toString().padStart(2, '0')
        val restSeconds = restMinutes % 60000
        val seconds = (restSeconds / 1000).toString().padStart(2, '0')
        val milliseconds = (restSeconds % 1000).toString().padStart(3, '0')
        return mapOf(Subs.HOURS to hours, Subs.MINUTES to minutes, Subs.SECONDS to seconds, Subs.MILLISECONDS to milliseconds)
    }
    fun getJustTheText(): String {
        return "\n${allStrings.joinToString("\n")}"
    }
    override fun toString(): String {
        return "${recreateLine(startTime)} --> ${recreateLine(endTime)}" +
                "\n${allStrings.joinToString("\n").trim()}"
    }
    fun toSpannable(): Spanned {
        return HtmlCompat.fromHtml(this.toString().split("\n").joinToString("<br>"),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
    }
    fun toSpannableJustTheText(): Spanned {
        return HtmlCompat.fromHtml(this.getJustTheText().split("\n").joinToString("<br>"),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
    }
}
