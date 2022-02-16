package com.subeditor.android_subtitle_editor

import java.io.Serializable

class SubtitleChange(val singleOrComplete: Int, val index: Int=0, val type : Int=0) : Serializable {
    companion object {
        const val INSERTION = 1
        const val DELETION = 2
        const val CHANGE = 3
        const val SINGLE = 5
        const val COMPLETE = 6
        val names = mapOf(1 to "INSERTION", 2 to "DELETION", 3 to "CHANGE", 5 to "SINGLE", 6 to "COMPLETE")
    }
    override fun toString(): String {
        return "SUBTITLE CHANGE - RANGE: ${names[singleOrComplete]}, INDEX: ${index}, TYPE: ${names[type]}"
    }
}