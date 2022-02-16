package com.subeditor.android_subtitle_editor

import android.app.Dialog
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.*
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import jcifs.smb.NtlmPasswordAuthentication
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileOutputStream
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class GetAuthDialog: DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val eea = LinearLayout(activity)
        eea.orientation = LinearLayout.VERTICAL
        val workgroupET = EditText(activity)
        workgroupET.editableText.insert(0, "WORKGROUPS")
        workgroupET.hint = "workgroup"
        val usernameET = EditText(activity)
        usernameET.hint = requireActivity().getString(R.string.username)
        val passwordET = EditText(activity)
        passwordET.hint = requireActivity().getString(R.string.password)
        eea.addView(workgroupET)
        eea.addView(usernameET)
        eea.addView((passwordET))
        val dd = android.app.AlertDialog.Builder(activity)
            .setTitle(requireActivity().getString(R.string.enter_credentials))
            .setView(eea)
            .setPositiveButton(requireActivity().getString(R.string.try_credentials)
            ) { _, _ ->
                val user = usernameET.text.toString().trim()
                val pass = passwordET.text.toString().trim()
                val workgroup = workgroupET.text.toString().trim()
                setFragmentResult("Credentials", bundleOf("user" to user,
                                                                           "pass" to pass,
                                                                           "workgroup" to workgroup ))

                dismiss()
            }
            .setNegativeButton(requireActivity().getString(R.string.cancel)
            ) {_, _ ->
                dismiss()
//                dialog.cancel()
            }
        return dd.show()
    }
}

object KnownServers {
    private lateinit var placedFragment: Fragment
    @kotlinx.serialization.Serializable
    var servers = mutableMapOf<Pair<String, String>, AddedServer>()
    private fun isoLateServerAndShareFromAddress(path: String): Pair<String, String> {
        val first = path.indexOf("//")
        val second = path.indexOf("/", first+2)
        val third = path.indexOf("/", second+1)
        val server = path.substring(first+2, second)
        val share = path.substring(second+1, third)
        return Pair(server, share)
    }
    private fun getServer(path: String): AddedServer {
        val serverAndShare = isoLateServerAndShareFromAddress(path)
        if (!servers.containsKey(serverAndShare)) {
            val newServer = AddedServer(serverAndShare.first, serverAndShare.second)
            servers[serverAndShare] = newServer
            return newServer
        }
        return servers[serverAndShare]!!
    }
    private const val fileNameForAll = "ServerInfo"
    private const val storageName = "serversSerialized"

    private fun createEncryption(fragment: Fragment): SharedPreferences {
        val keyScheme = EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV
        val valueScheme = EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        val masterKey = MasterKey.Builder(fragment.requireContext())
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences
                .create(fragment.requireContext(), fileNameForAll, masterKey, keyScheme, valueScheme)
    }
    private fun storeEncryption(fragment: Fragment) {
        val sharedPrefs = createEncryption(fragment)
        sharedPrefs.edit()?.putString(storageName, "")?.apply()
    }
    private fun restoreStrings(fragment: Fragment): String {
        return createEncryption(fragment).getString(storageName, "") as String
    }
    private fun backupServers(fragment: Fragment) {
        val serverJson = Json { allowStructuredMapKeys = true; ignoreUnknownKeys = true; prettyPrint = true }
        val serverSerialized = serverJson.encodeToString(servers)
        createEncryption(fragment).edit().putString(storageName, serverSerialized)?.apply()
    }
    fun handleEncryption(fragment: Fragment) {
        placedFragment = fragment
        if (!createEncryption(fragment).contains(storageName)) {
            storeEncryption(fragment)
            backupServers(fragment)
        } else {
            val serverJson = Json { allowStructuredMapKeys = true; ignoreUnknownKeys = true; prettyPrint = true }
            val oldServers :  MutableMap<Pair<String, String>, AddedServer> = serverJson.decodeFromString(restoreStrings(fragment))
            this.servers += oldServers
        }
    }
    private fun authenticateServer(server: AddedServer, fragment: Fragment) {
        val smb = server.getSmbFile()
        CoroutineScope(Dispatchers.IO).async {
            kotlin.runCatching {
                try {
                    smb.canRead()
                    server.verified = true
                    backupServers(placedFragment)
                } catch (e: Exception) {
                    fragment.requireActivity().runOnUiThread {
                        getCredentials(server, fragment)
                    }
                }
            }
        }.start()
    }
    fun getAuth(path: String, fragment: Fragment): NtlmPasswordAuthentication {
        val server = getServer(path)
        if (!server.verified) {
            authenticateServer(server, fragment)
        }
        return server.getCurrentNtlm()
    }
    private fun getCredentials(server: AddedServer, fragment: Fragment) {
        val getAuthDialog = GetAuthDialog()
        getAuthDialog.show(fragment.childFragmentManager, "")
        fragment.childFragmentManager.setFragmentResultListener("Credentials", fragment)
        { _, bundle ->
            val user = bundle.getString("user")
            val pass = bundle.getString("pass")
            val workgroup = bundle.getString("workgroup")
            server.user = user!!
            server.password = pass!!
            server.workgroup = workgroup!!
            (placedFragment as FragmentSMBExplorer).initiateUpdate()
        }
    }
    private fun exitToFragment(result: Boolean, path: String, fragment: Fragment) {
        when (fragment.javaClass.simpleName) {
            "FragmentSaveSubtitle" -> (fragment as FragmentSaveSubtitle).onResultOfLanSaving()
            "FragmentSMBExplorer"  -> (fragment as FragmentSMBExplorer).returnToSaveMenu(result, path)
        }
    }
    private fun askForVerification(textFile: String, pathString : String ="", fragment: Fragment) {
        val askForVerificationDialog = AskForVerificationDialog()
        askForVerificationDialog.pathString = pathString
        fragment.childFragmentManager.setFragmentResultListener("OverwriteResult", fragment)
            { _, bundle ->
                val overwrite = bundle.getBoolean("Overwrite")
                if (overwrite) {
                    saveStringToServerSimple(textFile, pathString, fragment)
                }
            }
        askForVerificationDialog.show(fragment.childFragmentManager, "")
    }
    fun saveStringToServerSimple(textFile: String, path: String, fragment: Fragment) {
        var result = false
        val dd = CoroutineScope(Dispatchers.IO).async {
            kotlin.runCatching {
                val ntlm = getAuth(path, fragment)
                val smbFile = SmbFile(path, ntlm)
                result = try {
                    SmbFileOutputStream(smbFile).write(textFile.toByteArray())
                    Thread.sleep(200)
                    SmbFile(path, ntlm).exists()
                } catch (e: java.lang.Exception) {
                    false
                }
            }
        }
        dd.start()
        dd.invokeOnCompletion {
            exitToFragment(result, path, fragment)
        }
    }
    fun saveStringToServer(textFile: String, path: String, ask: Boolean, fragment: Fragment) {
        CoroutineScope(Dispatchers.IO).async {
            kotlin.runCatching {
                val ntlm = getAuth(path, fragment)
                val smbFile = SmbFile(path, ntlm)
                if (smbFile.exists()) {
                   fragment.requireActivity().runOnUiThread {
                       askForVerification(textFile, path, fragment)
                   }
                } else {
                       saveStringToServerSimple(textFile, path, fragment)
                    }
            }
        }.start()
    }
}

@kotlinx.serialization.Serializable
class AddedServer(private val address: String, private val share: String) {
    var verified: Boolean = false
    var user = ""
    var password = ""
    var workgroup = ""
    fun getCurrentNtlm() : NtlmPasswordAuthentication{
        return if (user != "" && password != "") {
            NtlmPasswordAuthentication(workgroup, user, password)
        } else {
            NtlmPasswordAuthentication.ANONYMOUS
        }
    }
    fun getSmbFile(): SmbFile {
        val wholeAddress = "smb://$address/$share/"
        val ntlm = getCurrentNtlm()
        return SmbFile(wholeAddress, ntlm)
    }
    override fun toString() : String {
        return "SERVER ${address}, SHARE: ${share}, USER:${getCurrentNtlm().username} PASSWORD:${getCurrentNtlm().password} VERIFIED: $verified\n"
    }
}
