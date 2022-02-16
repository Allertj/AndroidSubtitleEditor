package com.subeditor.android_subtitle_editor

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.subeditor.android_subtitle_editor.databinding.FragmentSMBExplorerBinding
import jcifs.smb.NtlmPasswordAuthentication
import jcifs.smb.SmbFile
import kotlinx.coroutines.*

class SMBLocation(val name: String, val address: String)

class SMBCustomAdapter(private val dataSet: ArrayList<SMBLocation>, private val fragment: FragmentSMBExplorer) : RecyclerView.Adapter<SMBCustomAdapter.ViewHolder>() {
    class ViewHolder(view: View, private val dataSet: ArrayList<SMBLocation>, private val fragment: FragmentSMBExplorer) : RecyclerView.ViewHolder(view), View.OnClickListener{
        val shareButton: TextView = view.findViewById(R.id.subtitle_button)
        init {
            shareButton.setOnClickListener(this)
        }
        override fun onClick(v: View?) {
            val index1 = absoluteAdapterPosition
            fragment.initiateUpdate(dataSet[index1].address)
        }
    }
    @SuppressLint("NotifyDataSetChanged")
    fun updateLocations(newList: ArrayList<SMBLocation>) {
        dataSet.clear()
        dataSet.addAll(newList)
        notifyDataSetChanged()
    }
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.text_row_item, viewGroup, false)
        return ViewHolder(view, dataSet, fragment)
    }
    override fun getItemCount() = dataSet.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.shareButton.text = dataSet[position].name
    }

}
class AddressBar(private val binder: FragmentSMBExplorerBinding, private val fragmentSMBExplorer: FragmentSMBExplorer ) {
    private fun createMapFromAddress(path: String): MutableMap<String, String> {
        val dde: MutableMap<String, String> = mutableMapOf()
        var wholePath = path.substringAfter("smb://")
        val lst = wholePath.split("/").size
        var smb = "smb:/"
        for (count in 0..lst - 2) {
            val currentPath = wholePath.substringBefore("/")
            smb += "/$currentPath"
            dde["$currentPath/"] = smb
            wholePath = wholePath.substringAfter("/")
        }
        return dde
    }
    private fun addTextView(dirname: String, smbAddress: String) {
        val mainBody = binder.listOfDirectories
        val newText = TextView(fragmentSMBExplorer.requireContext())
        val aboveLine = " > " + dirname.dropLast(1)
        newText.maxLines = 1
        newText.isClickable = true
        newText.isFocusable = true
        newText.text = aboveLine
        newText.setOnClickListener {
            fragmentSMBExplorer.initiateUpdate("$smbAddress/")
        }
        mainBody.addView(newText)
    }
    fun addAddressToTop(path: String) {
        val listOfDirs = binder.listOfDirectories
        listOfDirs.removeAllViews()
        val newText = TextView(fragmentSMBExplorer.requireActivity())
        val textLine = "smb://"
        newText.isClickable = true
        newText.text = textLine
        newText.isFocusable = true
        newText.setOnClickListener {
            fragmentSMBExplorer.initiateUpdate("smb://")
        }
        listOfDirs.addView(newText)
        val mapOfAddresses = createMapFromAddress(path)
        mapOfAddresses.forEach {
            addTextView(it.key, it.value)
        }
    }
}
class SMBControl {
    //TODO Switch Around:
//    var currentLocation = "smb://192.168.0.177/"
    var currentLocation = "smb://"

    private fun findShares(path:String, ntlm: NtlmPasswordAuthentication) : ArrayList<SMBLocation>{
        val share = SmbFile(path, ntlm)
        val subDirsAndFilesInArray = arrayListOf<SMBLocation>()
            share.listFiles().forEach {
                var newName = it.name
                if (it.name.endsWith("/")) {
                    newName = it.name.dropLast(1)
                }
                val new = SMBLocation(newName, path + it.name)
                subDirsAndFilesInArray.add(new)
            }
        subDirsAndFilesInArray.sortBy { it.name }
        return subDirsAndFilesInArray
    }
    private fun findServers(): ArrayList<SMBLocation> {
        val mapOfWorkGroup = findShares("smb://", NtlmPasswordAuthentication.ANONYMOUS)
        val resultMap = arrayListOf<SMBLocation>()
        mapOfWorkGroup.forEach {
            resultMap += findShares(it.address, NtlmPasswordAuthentication.ANONYMOUS)
        }
        val endResults = arrayListOf<SMBLocation>()
        resultMap.forEach {
            val serverRegex = """(.{4}\/\/).+?\/(.+)""".toRegex()
            val dd = serverRegex.find(it.address)?.groupValues?.get(1)
            val ee = serverRegex.find(it.address)?.groupValues?.get(2)
            val new = SMBLocation(it.name, dd+ee)
            endResults.add(new)
        }
        endResults.sortBy { it.name }
        return endResults
    }
    fun checkFreeSpace(activity: FragmentSMBExplorer) {
        CoroutineScope(Dispatchers.IO).async {
            kotlin.runCatching {
                val ntlm = KnownServers.getAuth(currentLocation, activity)
                val freeSpace = SmbFile(currentLocation, ntlm).diskFreeSpace
                activity.requireActivity().runOnUiThread {
                    activity.updateFreeSpace(freeSpace)
                }
            }
        }.start()
    }
    fun updateLocation(activity: FragmentSMBExplorer, activity1: Activity) {
        var results = arrayListOf<SMBLocation>()
        activity.binding.loadingTextSmb.text = activity1.getString(R.string.loading)
        CoroutineScope(Dispatchers.Default).async {
            when {
                currentLocation == "smb://" -> {
                    results = findServers()
                    activity.getResults(currentLocation, results)
                }
                currentLocation.count{c -> c == '/'} <= 3 -> {
                    results = findShares(currentLocation, NtlmPasswordAuthentication.ANONYMOUS)
                    activity.getResults(currentLocation, results)
                }
                else -> {
                    val ntlm = KnownServers.getAuth(currentLocation, activity)
                    results = findShares(currentLocation, ntlm)
                }
            }
        }.invokeOnCompletion {
            if (activity.isAdded) {
                activity.requireActivity().runOnUiThread {
                    activity.binding.loadingTextSmb.text = ""
                }
                activity.getResults(currentLocation, results)
            }
        }
    }
}

class FragmentSMBExplorer : Fragment() {
        private var _binding: FragmentSMBExplorerBinding? = null
        val binding get() = _binding!!
        private lateinit var wholeView : View
        private lateinit var adapter: SMBCustomAdapter
        private lateinit var smbControl: SMBControl
        private lateinit var addressBar: AddressBar
        private lateinit var toolbar: ToolBarHelper
        private var showToolbar : Boolean = false
        private var request = "OpenSelfSub"
        private var history = arrayListOf("smb://")
        private var listAllFiles = false
        private var receivedExtensions = arrayListOf("/")
        private val currentSub: CurrentSub by activityViewModels()

    fun returnToSaveMenu(savedSuccessfully: Boolean, path: String) {
        if (savedSuccessfully) {
            currentSub.addSubtitleLocation(path, CurrentSub.STRING, CurrentSub.LAN, path)
            currentSub.backupSession(this, otherCall = true)
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), getString(R.string.file_successfully_saved), Toast.LENGTH_LONG).show()
                val action = FragmentSMBExplorerDirections.actionFragmentSMBExplorerToFragmentMainMenu()
                findNavController().navigate(action)
            }
        } else {
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), getString(R.string.file_not_saved), Toast.LENGTH_LONG).show()
            }
        }
    }
    private fun checkFileSize(fileSize: Long, path: String, requestCode: Int) {
        requireActivity().runOnUiThread {
            if (fileSize < Subs.IMPROBABLE_FILE_SIZE) {
                transmitResultSubFinal(path, requestCode)
            } else {
                val askConfirmationFragment = AskConfirmationFragment()
                askConfirmationFragment.message = getString(R.string.ask_if_size_is_big)
                askConfirmationFragment.show(childFragmentManager, "")
                childFragmentManager.setFragmentResultListener("requestConfirm", this)
                { _, bundle ->
                    val resultReceived = bundle.getBoolean("Confirmed")
                    if (resultReceived) {
                        transmitResultSubFinal(path, requestCode)
                    }
                }
            }
        }
    }
    private fun transmitResultSubFinal(path: String, requestCode: Int) {
        var results = ByteArray(2)
        CoroutineScope(Dispatchers.IO).async {
            kotlin.runCatching {
                try {
                    val ntlm = KnownServers.getAuth(path, this@FragmentSMBExplorer)
                    val kk = SmbFile(path, ntlm)
                    results = kk.inputStream.readBytes()
                } catch (e : Exception) {
                    Toast.makeText(requireContext(), getString(R.string.a_error_has_occurred), Toast.LENGTH_SHORT).show()
                }
            }
        }.invokeOnCompletion {
            val fileCheck = NewFileChecking(requestCode, this)
            fileCheck.filePath = path
            requireActivity().runOnUiThread {
                fileCheck.checkSequence(this, path, results)
            }
        }
    }
    private fun transmitResultSub(path: String, requestCode: Int) {
        CoroutineScope(Dispatchers.IO).async {
            kotlin.runCatching {
                try {
                    val ntlm = KnownServers.getAuth(path, this@FragmentSMBExplorer)
                    val kk = SmbFile(path, ntlm)
                    checkFileSize(kk.length(), path, requestCode)
                } catch (e: Exception) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.a_error_has_occurred),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    private fun saveSubtitle(path: String) {
        KnownServers.saveStringToServer(currentSub.subtitle.toString(), path, true, this)
    }
    private fun transmitResult(path: String) {
        when (request) { "OpenSelfSub"     -> { transmitResultSub(path, NewFileChecking.FOR_LAN_OPENING) }
                         "OpenVideo"       -> { transmitResultVideo(path) }
                         "OpenOtherSub"    -> { transmitResultSub(path, NewFileChecking.FOR_SYNC_OPENING_LAN) }
                         "SaveSMBSubtitle" -> { saveSubtitle(path)
            }
        }
    }
    fun subtitleLoaded() {
        findNavController().navigate(R.id.fragmentMainMenu)
    }
    fun subtitleLoadedForSync() {
        findNavController().navigate(R.id.fragmentSyncWithOtherSub)
    }
    private fun transmitResultVideo(path: String) {
        currentSub.addVideoLocation(path, CurrentSub.STRING, CurrentSub.LAN, path, this)
        PlayData.currentPosition = 0
        PlayData.currentAudioTrack = 0
        CurrentServer.server = null
        findNavController().navigate(R.id.fragmentSelectVideo)
    }

    fun getResults(currentLocation:String, results: ArrayList<SMBLocation>) {
        activity?.runOnUiThread {
            if (results.isEmpty()) { binding.emptyDirectory.visibility = View.VISIBLE }
            else                   { binding.emptyDirectory.visibility = View.GONE }
        }
        var newResults = arrayListOf<SMBLocation>()
        if (!listAllFiles) {
            results.forEach { smbLocation ->
                receivedExtensions.forEach {
                    if (smbLocation.address.endsWith(it)) {
                        newResults.add(smbLocation)
                    }
                }
            }
        } else {
            newResults = results
        }
        Handler(Looper.getMainLooper()).post(Runnable {
            adapter.updateLocations(newResults)
            addressBar.addAddressToTop(currentLocation)
        })
    }
    fun updateFreeSpace(freeSpace: Long) {
        val freeSpaceInGb = freeSpace / 1024 / 1024/ 1024
        val new = "${getString(R.string.free_space)} \n$freeSpaceInGb ${getString(R.string.gb)}"
        binding.freeSpace.text = new
    }
    fun initiateUpdate(newLocation: String="") {
        when {
            newLocation == "" -> {
                smbControl.updateLocation(this, requireActivity())
            }
            newLocation.endsWith("/") -> {
                history.add(newLocation)
                smbControl.currentLocation = newLocation
                smbControl.updateLocation(this, requireActivity())
            }
            receivedExtensions.contains(newLocation.substringAfterLast(".")) -> {
                runBlocking {
                    if (request != "SaveSMBSubtitle") {
                        transmitResult(newLocation)
                    } else {
                        val filename = "${newLocation.substringBeforeLast(".")}.srt"
                            .substringAfterLast("/")
                        binding.savingBar.saveFilenameEditToolbar.setText(filename)
                    }
                }
            }
        }
    }
    private fun createSaving() {
        binding.savingBar.savingBar.visibility = View.VISIBLE
        val filename = binding.savingBar.saveFilenameEditToolbar
        val filenameWithSrt = "${currentSub.subFileName.substringBeforeLast(".")}.srt"
        filename.setText(filenameWithSrt)
        binding.savingBar.saveSmbButtonToolbar.setOnClickListener {
            if (smbControl.currentLocation.count{c -> c == '/'} < 4) {
                Toast.makeText(this.requireContext(), getString(R.string.not_a_valid_lan_location), Toast.LENGTH_SHORT).show()
            } else {
                val newFileName = filename.text.toString().substringBeforeLast(".") + ".srt"
                val path = smbControl.currentLocation
                transmitResult(path + newFileName)
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity?.onBackPressedDispatcher?.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    smbControl.currentLocation == "smb://" -> {
                        val action = FragmentSMBExplorerDirections
                                 .actionFragmentSMBExplorerToFragmentMainMenu()
                        wholeView.findNavController().navigate(action)
                    }
                    history.size > 1 -> {
                        smbControl.currentLocation = history[history.lastIndex - 1]
                        smbControl.updateLocation(this@FragmentSMBExplorer, requireActivity())
                        history.removeAt(history.lastIndex)
                    }
                    else -> {
                        val action = FragmentSMBExplorerDirections
                                     .actionFragmentSMBExplorerToFragmentMainMenu()
                        wholeView.findNavController().navigate(action)
                    }
                }
            }
        })
    }
        override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            _binding = FragmentSMBExplorerBinding.inflate(inflater, container, false)
            wholeView = binding.root
            val view = binding.root
            adapter = SMBCustomAdapter(arrayListOf(), this)
            KnownServers.handleEncryption(this)
            val subtitleView = binding.smbRecycler
            subtitleView.adapter = adapter
            subtitleView.layoutManager = LinearLayoutManager(requireActivity())
            smbControl = SMBControl()
            addressBar = AddressBar(binding, this)

            listAllFiles = FragmentSMBExplorerArgs.fromBundle(requireArguments()).listAllFiles
            val savingLocation = FragmentSMBExplorerArgs.fromBundle(requireArguments()).savingLocation
            val extensions = FragmentSMBExplorerArgs.fromBundle(requireArguments()).extensions
            val startLocation = FragmentSMBExplorerArgs.fromBundle(requireArguments()).startLocation
            showToolbar = FragmentSMBExplorerArgs.fromBundle(requireArguments()).showToolbar
            request = FragmentSMBExplorerArgs.fromBundle(requireArguments()).request
            if (startLocation != "") {
                smbControl.currentLocation = startLocation
            }
            smbControl.updateLocation(this, requireActivity())
            receivedExtensions += extensions
            if (savingLocation) {
                createSaving()
            }
            binding.refreshButton.setOnClickListener {
                smbControl.updateLocation(this, requireActivity())
            }
            binding.checkFreeSpace.setOnClickListener {
                smbControl.checkFreeSpace(this)
            }
            if (showToolbar) {
                toolbar = ToolBarHelper()
                toolbar.getToolbar(this)
                toolbar.hideItems(
                    listOf(
                        R.id.view_toolbar,
                        R.id.play_toolbar,
                        R.id.redo_toolbar,
                        R.id.undo_toolbar,
                        R.id.save_toolbar,
                        R.id.help_toolbar
                    )
                )
            } else {
                activity?.findViewById<Toolbar>(R.id.toolbar)?.visibility = View.GONE
            }
            return view
        }
        override fun onResume() {
            if (showToolbar) {
                toolbar.showToolbar()
            }
            super.onResume()
        }
    override fun onDetach() {
        if (this::toolbar.isInitialized) {
            toolbar.hideToolbar()
        }
        super.onDetach()
    }
        override fun onDestroyView() {
            super.onDestroyView()
            _binding = null
        }

}