package com.subeditor.android_subtitle_editor

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CurrentSub : ViewModel() {
    companion object {
        const val LOCAL = 1
        const val LAN = 2
        const val INTERNET = 3
        const val URI = 0
        const val STRING = 1
        const val NONE = -1
    }
    fun addSubtitle(createdSubtitle: Subtitle) {
        subtitle = createdSubtitle
    }
    var newUriIntent : Uri? = null
    lateinit var subtitle: Subtitle
    var videoLocation : Any? = null
    private var typeOfVideoLocation = 0
    var videoLocatedAt = 0
    private var videoFileName = ""

    var subtitleLocation : Any? = null
    private var typeOfSubtitleLocation = 0
    var subtitleLocatedAt = 0
    var subFileName = ""
    var actualVideoLocation = ""
    var actualSubtitleLocation = ""
    var loadedFromFileExplorer = false

    fun addVideoLocation(location: Any?, typeOfLocation: Int, locatedAt: Int, actualVideoLoc: String, fragment: Fragment) {
        videoLocation = location
        typeOfVideoLocation = typeOfLocation
        videoLocatedAt = locatedAt
        actualVideoLocation = actualVideoLoc
        videoFileName = actualVideoLoc.substringAfterLast("/")
        actualBackupSession(fragment)
    }
    fun addSubtitleLocation(location: Any?, typeOfLocation: Int, locatedAt: Int, actualSubLoc: String) {
        subtitleLocation = location
        actualSubtitleLocation = actualSubLoc
        typeOfSubtitleLocation = typeOfLocation
        subtitleLocatedAt = locatedAt
        subFileName = actualSubLoc.substringAfterLast("/")
    }
    private fun resetVideoValues(fragment: Fragment) {
        addVideoLocation(null, NONE, NONE, "", fragment)
    }
    fun convertDecimalToSRTTimeNotation(decimal: Int) : String {
        val hours = (decimal / 3600000).toString().padStart(2, '0')
        val restMinutes = decimal % 3600000
        val minutes = (restMinutes / 60000).toString().padStart(2, '0')
        val restSeconds = restMinutes % 60000
        val seconds = (restSeconds / 1000).toString().padStart(2, '0')
        val milliseconds = (restSeconds % 1000).toString().padStart(3, '0')
        return "${hours}:${minutes}:${seconds},${milliseconds}"
    }
    private fun backupStrings(fragment: Fragment, name: String, value: String) {
        fragment.activity
            ?.getSharedPreferences("Subtitle", Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(name, value)?.apply()
    }
    private fun backupInts(fragment: Fragment, name: String, value: Int) {
        fragment.activity
            ?.getSharedPreferences("Subtitle", Context.MODE_PRIVATE)
            ?.edit()
            ?.putInt(name, value)?.apply()
    }
    fun wipeLastSession(fragment: Fragment) {
        resetVideoValues(fragment)
        ReceivedInfo.resetValues()
        PlayData.resetValues()
    }
    private fun actualBackupSession(fragment: Fragment) {
        val values = mapOf("typeOfVideoLocation"    to typeOfVideoLocation,
            "videoLocatedAt"         to videoLocatedAt,
            "typeOfSubtitleLocation" to typeOfSubtitleLocation,
            "subtitleLocatedAt"      to subtitleLocatedAt,
            "BackupPresent"          to 1)

        val stringValues = mapOf(  //"subtitle"               to retrieved,
            "videoFileName"          to videoFileName,
            "actualSubtitleLocation" to actualSubtitleLocation,
            "subFileName"            to subFileName,
            "actualVideoLocation"    to actualVideoLocation,
            "videoLocation"          to videoLocation.toString(),
            "subtitleLocation"       to subtitleLocation.toString())
        stringValues.forEach { (backupStrings(fragment, it.key, it.value)) }
        values.forEach       { (backupInts(fragment, it.key, it.value)) }
    }
    private fun backupSubtitle(fragment: Fragment) {
        val retrieved1 = Json.encodeToString(subtitle)
        backupStrings(fragment, "subtitle", retrieved1)
    }
    fun backupPlayData(fragment: Fragment) {
        backupInts(fragment, "currentPosition", PlayData.currentPosition.toInt())
        backupInts(fragment, "currentAudioTrack", PlayData.currentAudioTrack)
    }
    fun backupReceivedInfo(fragment: Fragment){
        backupStrings(fragment, "ReceivedInfo", Json.encodeToString(ReceivedInfo.values1))
    }
    private fun restoreNight(fragment: Fragment): Boolean {
        val kk = fragment.activity
            ?.getSharedPreferences("Subtitle", Context.MODE_PRIVATE)
            ?.getBoolean("NightTheme", false)
        return kk as Boolean
    }
    fun setNightTheme(fragment: Fragment) {
        if (restoreNight(fragment)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }
    fun backupNightTheme(fragment: Fragment, active: Boolean) {
        fragment.activity
            ?.getSharedPreferences("Subtitle", Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean("NightTheme", active)?.apply()
    }
    fun initiateServer(fragment: Fragment){
        if (CurrentServer.server == null) {
            CreateServer().loadServer(videoLocation as String, fragment)
        }
        if (!CurrentServer.server!!.isAlive) {
            CurrentServer.server?.start()
        }
        fragment.findNavController().navigate(R.id.fragmentNewPlayer)
    }
    fun backupSession(fragment: Fragment, subtitleChange: SubtitleChange?=null, otherCall: Boolean=false) {
        if (subtitleChange != null || otherCall) {
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    if (otherCall) {
                        backupPlayData(fragment)
                        backupReceivedInfo(fragment)
                    }
                    actualBackupSession(fragment)
                    backupSubtitle(fragment)
                } catch (e: Exception) {
//                    Toast.makeText(fragment.requireContext(), e.localizedMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    fun checkBackup(fragment: Fragment): Boolean {
        val backupPresent = fragment.activity
            ?.getSharedPreferences("Subtitle", Context.MODE_PRIVATE)
            ?.getInt("BackupPresent", 0)
        return backupPresent != 0
    }
    private fun restoreStrings(fragment: Fragment, name: String): String {
        return fragment.activity
            ?.getSharedPreferences("Subtitle", Context.MODE_PRIVATE)
            ?.getString(name, "") as String
    }
    private fun restoreInts(fragment: Fragment, name: String): Int {
        return fragment.activity
            ?.getSharedPreferences("Subtitle", Context.MODE_PRIVATE)
            ?.getInt(name, 0) as Int
    }

    fun restoreSession(fragment: Fragment){
        PlayData.currentPosition   = restoreInts(fragment, "currentPosition").toLong()
        PlayData.currentAudioTrack = restoreInts(fragment, "currentAudioTrack")

        typeOfVideoLocation =    restoreInts(fragment,"typeOfVideoLocation"     )
        videoLocatedAt =         restoreInts(fragment,"videoLocatedAt"          )
        typeOfSubtitleLocation = restoreInts(fragment,"typeOfSubtitleLocation"  )
        subtitleLocatedAt =      restoreInts(fragment,"subtitleLocatedAt"       )

        subtitle = Json.decodeFromString(restoreStrings(fragment, "subtitle"))
        actualSubtitleLocation =  restoreStrings(fragment, "actualSubtitleLocation")
        videoFileName          =  restoreStrings(fragment, "videoFileName"         )
        subFileName            =  restoreStrings(fragment, "subFileName"           )
        actualVideoLocation    =  restoreStrings(fragment, "actualVideoLocation"   )
        subtitle.resetListeners()
        videoLocation = if (typeOfVideoLocation == URI) { Uri.parse(restoreStrings(fragment, "videoLocation"))
        } else { restoreStrings(fragment, "videoLocation")
        }
        subtitleLocation = if (typeOfSubtitleLocation == URI) { Uri.parse(restoreStrings(fragment, "subtitleLocation"))
        } else { restoreStrings(fragment, "subtitleLocation")
        }
        ReceivedInfo.values1 = Json.decodeFromString(restoreStrings(fragment, "ReceivedInfo"))
        KnownServers.handleEncryption(fragment)
    }
}
