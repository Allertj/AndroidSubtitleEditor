package com.subeditor.android_subtitle_editor

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.Test
import org.junit.Assert.*
import org.junit.Rule
import java.io.File
import kotlin.math.roundToInt

class UnitTests {
    @Rule
    @JvmField
    val instantExecutorRule = InstantTaskExecutorRule()
    fun openSubtitle() : Subtitle {
        val directoryWithLotsOfSrtFiles = "/home/allert/random_srt_files"
        val sub = File(directoryWithLotsOfSrtFiles).walk()
        val fileindex = (0..sub.count()).random()
        var bytes = ByteArray(0)
        var filename = ""
        sub.forEachIndexed{index, file -> if (index == fileindex)
                bytes = file.inputStream().readBytes()
                filename = file.absolutePath
        }
        return SubtitleFactory(bytes, filename).createSub()
    }
    @Test
    fun testDeletion() {
        val subtitle = openSubtitle()
        val firstSize = subtitle.size()
        subtitle.removeAt((0..firstSize).random())
        assertEquals(firstSize-1, subtitle.size())
        subtitle.undoLastAction()
        assertEquals(firstSize, subtitle.size())
        subtitle.redoLastAction()
        assertEquals(firstSize-1, subtitle.size())
    }
    @Test
    fun testSwipeDeletion() {
        val subtitle = openSubtitle()
        val firstSize = subtitle.size()
        subtitle.removeAtBySwipe((0..firstSize).random())
        assertEquals(firstSize-1, subtitle.size())
        subtitle.undoLastAction()
        assertEquals(firstSize, subtitle.size())
        subtitle.redoLastAction()
        assertEquals(firstSize-1, subtitle.size())
    }
    @Test
    fun testShiftForwards() {
        val subtitle = openSubtitle()
        val someSub = (0..subtitle.size()).random()
        val start = subtitle.getLine(someSub).startTime
        val end = subtitle.getLine(someSub).endTime
        subtitle.shift(1000)
        assertEquals(subtitle.getLine(someSub).startTime, start+1000)
        assertEquals(subtitle.getLine(someSub).endTime, end+1000)
    }
    @Test
    fun testShiftBackwards() {
        val subtitle = openSubtitle()
        val someSub = (0..subtitle.size()).random()
        val start = subtitle.getLine(someSub).startTime
        val end = subtitle.getLine(someSub).endTime
        subtitle.shift(-1000)
        assertEquals(subtitle.getLine(someSub).startTime, start-1000)
        assertEquals(subtitle.getLine(someSub).endTime, end-1000)
    }
    @Test
    fun testStretchForwards() {
        val subtitle = openSubtitle()
        val someSub = (0..subtitle.size()).random()
        val start = subtitle.getLine(someSub).startTime
        subtitle.stretch(1.4)
        assertEquals(subtitle.getLine(someSub).startTime.toDouble(), (start*1.4).roundToInt().toDouble(), 1.0)
    }


    @Test
    fun testNewStart(){
        val subtitle = openSubtitle()
        subtitle.giveNewTimes(1000, 3600000)
        assertEquals(subtitle.getLine(0).startTime, 1000)
        assertEquals(subtitle.getLine(subtitle.size()-1).startTime, 3600000)
    }
    @Test
    fun testGiveNewStartTimeSeparate(){
        val subtitle = openSubtitle()
        val someSub = (0..subtitle.size()).random()
        val start = subtitle.getLine(someSub).startTime
        subtitle.giveNewStartTimeInSeparateTimeUnits(0, 2, 2, 2)
        assertEquals(subtitle.getLine(0).startTime, 122002)
        subtitle.undoLastAction()
        assertEquals(subtitle.getLine(someSub).startTime, start)
   }
    @Test
    fun testGiveNewEndTimeSeparate(){
        val subtitle = openSubtitle()
        val someSub = (0..subtitle.size()).random()
        val start = subtitle.getLine(someSub).startTime
        subtitle.giveNewEndTimeInSeparateTimeUnits(1, 2, 2, 2)
        assertEquals(3722002.toDouble(), subtitle.getLine(subtitle.size()-1).startTime.toDouble(), 2.0)
        subtitle.undoLastAction()
        assertEquals(start.toDouble(), subtitle.getLine(someSub).startTime.toDouble(), 2.0)
        subtitle.redoLastAction()
        assertEquals(3722002.toDouble(), subtitle.getLine(subtitle.size()-1).startTime.toDouble(), 2.0)
    }
    @Test
    fun testSyncManual(){
        val subtitle = openSubtitle()
        val someSub = (0..subtitle.size()).random()
        val start = subtitle.getLine(someSub).startTime
        val end = subtitle.getLine(someSub).endTime
        subtitle.syncSubtitlesToManualChoice(1000, 3600000, 5, 20)
        assertEquals(subtitle.getLine(5).startTime, 1000)
        assertEquals(subtitle.getLine(20).startTime, 3600000)
        subtitle.undoLastAction()
        assertEquals(subtitle.getLine(someSub).startTime, start)
        assertEquals(subtitle.getLine(someSub).endTime, end)
    }

    private fun createSubtitleLine() : SubtitleLine {
        return SubtitleLine(
            20000, 18061100, 18062100, 1000,
            arrayListOf("Something", "Something line2")
        )
    }
    @Test
    fun createSubtitleLineTest() {
        val newSubtitleLine = createSubtitleLine()
        assertEquals(newSubtitleLine.getJustTheText(), "\nSomething\nSomething line2")
        assertEquals(newSubtitleLine.toString(), "05:01:01,100 --> 05:01:02,100\nSomething\nSomething line2")
    }
    @Test
    fun insertSubtitleLineTest() {
        val subtitle = openSubtitle()
        val newSubtitleLine = createSubtitleLine()
        val oldLength = subtitle.size()
        subtitle.insertSubtitleLine(subtitle.size(), newSubtitleLine)
        assertEquals(oldLength + 1, subtitle.size())
    }
    @Test
    fun getTimeLines() {
        val subtitle = openSubtitle()
        val newSubtitleLine = createSubtitleLine()
        subtitle.insertSubtitleLine(subtitle.size(), newSubtitleLine)
        assertEquals(subtitle.getEndTimeAsString(subtitle.size()-1), "05:01:02,100")
        assertEquals(subtitle.getStartTimeAsString(subtitle.size()-1), "05:01:01,100")
    }
    @Test
    fun insertTextAtIndex() {
        val subtitle = openSubtitle()
        val newSubtitleLine = createSubtitleLine()
        subtitle.insertSubtitleLine(subtitle.size(), newSubtitleLine)
        subtitle.insertTextIntoSubtitle(subtitle.size()-1, "New text\nNewText")
        assertEquals(subtitle.getLine(subtitle.size()-1).getJustTheText(), "\nNew text\nNewText")
        subtitle.undoLastAction()
        assertEquals(subtitle.getLine(subtitle.size()-1).getJustTheText(), "\n\nSomething\nSomething line2")
    }

}