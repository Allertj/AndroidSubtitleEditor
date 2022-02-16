package com.subeditor.android_subtitle_editor

import android.app.Application
import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.findNavController
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.CaptionStyleCompat
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.util.MimeTypes
import com.subeditor.android_subtitle_editor.databinding.FragmentNewPlayerBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.KeyStore
import java.util.*
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

object PlayData {
    var currentPosition = 0.toLong()
    var currentAudioTrack = -1
    var currentMediaItem : MediaItem? = null

    fun setPosition(position: Long) {
        currentPosition = position
    }
    fun setAudioTrack(trackIndex: Int) {
        currentAudioTrack = trackIndex
    }
    fun setCurrentMedia(mediaItem: MediaItem) {
        currentMediaItem = mediaItem
    }
    fun resetValues() {
        currentPosition = 0
        currentAudioTrack = 0
        currentMediaItem = null
    }
}

class NavigationListener (private val playerViewModel: PlayerViewModel) : NavController.OnDestinationChangedListener {
    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        if (destination.label != "FragmentNewPlayer") {
            playerViewModel.savePositionAndStop()
            controller.removeOnDestinationChangedListener(this)
        }
    }
}

class PlayerEventListener(private val playerView: PlayerView) : Player.Listener {
    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)
        playerView.keepScreenOn =
            !(playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED)
    }
}
class NewObserver(private val setUpPlayer: () -> Unit , private val releaseExoPlayer: () -> Unit) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        super.onCreate(owner)
        setUpPlayer()
    }
    override fun onStop(owner: LifecycleOwner) {
        releaseExoPlayer()
        super.onStop(owner)
    }
}

class ConfirmSyncDialog: DialogFragment() {
    private val currentSub: CurrentSub by activityViewModels()
    var startOrEnd = true
    var cur = 0
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        if (savedInstanceState != null) { cur = savedInstanceState.getInt("Cur")}
        val time = currentSub.convertDecimalToSRTTimeNotation(cur)
        var message = getString(R.string.do_you_want_the_first_line_to_sync_with)
        if (!startOrEnd) {message = getString(R.string.do_you_want_the_last_line_to_sync_with)}
        val dd = android.app.AlertDialog.Builder(requireContext())
            .setMessage(message + " ${time}?")
            .setPositiveButton(getString(R.string.ok)) { _, _->
                if (startOrEnd) {
                    currentSub.subtitle.syncSubtitlesToManualChoice(cur,
                        currentSub.subtitle.getLine(ReceivedInfo.getValue(ReceivedInfo.PLAY_DATA_LAST)).startTime,
                        ReceivedInfo.getValue(ReceivedInfo.PLAY_DATA_FIRST),
                        ReceivedInfo.getValue(ReceivedInfo.PLAY_DATA_LAST))
//                    updateText()
                }  else {
                    currentSub.subtitle.syncSubtitlesToManualChoice(
                        currentSub.subtitle.getLine(ReceivedInfo.getValue(ReceivedInfo.PLAY_DATA_FIRST)).startTime,
                        cur,
                        ReceivedInfo.getValue(ReceivedInfo.PLAY_DATA_FIRST),
                        ReceivedInfo.getValue(ReceivedInfo.PLAY_DATA_LAST))
//                    updateText()
                }
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
            }
        return dd.show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("Cur", cur)
        super.onSaveInstanceState(outState)
    }
}

class PlayerViewModel(application: Application): AndroidViewModel(application), LifecycleObserver {
    private val _player = MutableLiveData<Player?>()
    val player: LiveData<Player?> get() = _player
    private val _ts = MutableLiveData<DefaultTrackSelector?>()
    private val ts: LiveData<DefaultTrackSelector?> get() = _ts
    private var contentPosition = 0L
    private var playWhenReady = true

    private var currentObserver: NewObserver
    init {
        currentObserver = NewObserver({ setUpPlayer() }, { releaseExoPlayer() })
        ProcessLifecycleOwner.get().lifecycle.addObserver(currentObserver)
    }
    fun jumpToAGivenPoint(positionInMills: Long) {
        player.value?.pause()
        player.value?.seekTo(positionInMills)
        player.value?.pause()
    }
    fun reloadSubtitle(location: String) {
        val simpleExoplayer = player.value!!
        val trackSelector = ts.value!!
        val currentMedia = simpleExoplayer.currentMediaItem
        val newSub = MediaItem.SubtitleConfiguration
            .Builder(Uri.parse(location))
            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
            .setRoleFlags(C.ROLE_FLAG_ALTERNATE)
            .build()
        val newMediaItem = currentMedia?.buildUpon()?.setSubtitleConfigurations(listOf(newSub))?.build()
        val latest = getMapOfTrackByType(C.TRACK_TYPE_TEXT, trackSelector).size-1
        val tim = simpleExoplayer.currentPosition
        simpleExoplayer.setMediaItem(newMediaItem!!)
        simpleExoplayer.seekTo(tim)
        val newListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                val typeValue = getTypeIndex(trackSelector, C.TRACK_TYPE_TEXT)
                switchToDifferentTrack(trackSelector, typeValue, latest)
                simpleExoplayer.removeListener(this)
            }
        }
        simpleExoplayer.addListener(newListener)
    }
    fun cycleThroughAudio(): String? {
        val trackSelector = ts.value!!
        val mapOfAudioChannels = getMapOfTrackByType(C.TRACK_TYPE_AUDIO, trackSelector)
        val audioCount = mapOfAudioChannels.size
        if (PlayData.currentAudioTrack == -1 || PlayData.currentAudioTrack == audioCount-1) {
//            PlayData.currentAudioTrack = 0
            PlayData.setAudioTrack(0)
        } else {
            PlayData.setAudioTrack(PlayData.currentAudioTrack + 1)
        }
        val typeValue = getTypeIndex(trackSelector, C.TRACK_TYPE_AUDIO)
        switchToDifferentTrack(trackSelector, typeValue, PlayData.currentAudioTrack)
        return mapOfAudioChannels[PlayData.currentAudioTrack]
    }
    fun setUpChosenAudioTrack() {
        val trackSelector = ts.value!!
        val typeValue = getTypeIndex(trackSelector, C.TRACK_TYPE_AUDIO)
        switchToDifferentTrack(trackSelector, typeValue, PlayData.currentAudioTrack)
    }
    fun getMapOfTrackByType(type: Int, trackSelector: DefaultTrackSelector): MutableMap<Int, String> {
        val mappedTrackInfo = trackSelector.currentMappedTrackInfo
        var foundIndex = 0
        for (rendererIndex in 0 until mappedTrackInfo!!.rendererCount) {
            val trackType = mappedTrackInfo.getRendererType(rendererIndex)
            if (trackType == type) {
                foundIndex = rendererIndex
                break
            }
        }
        val trackGroupArray = trackSelector.currentMappedTrackInfo!!.getTrackGroups(foundIndex)
        val tracks = mutableMapOf<Int, String>()
        for (groupIndex in 0 until trackGroupArray.length) {
            val sss = trackGroupArray.get(groupIndex).getFormat(0)
            val lang = if (sss.language != null) Locale(sss.language as String).displayLanguage else "null"
            tracks[groupIndex] = lang + " " + sss.label
        }
        return tracks
    }
    fun getTypeIndex(trackSelector: DefaultTrackSelector, type : Int): Int {
        for (rendererIndex in 0 until trackSelector.currentMappedTrackInfo!!.rendererCount) {
            val trackType = trackSelector.currentMappedTrackInfo!!.getRendererType(rendererIndex)
            if (trackType == type) {
                return rendererIndex
            }
        }
        return -1
    }
    fun switchToDifferentTrack(trackSelector: DefaultTrackSelector, type: Int, trackIndex : Int) {
        val override = DefaultTrackSelector.SelectionOverride(trackIndex, 0)
        val trackGroupArray = trackSelector.currentMappedTrackInfo!!.getTrackGroups(type)
        trackSelector.parameters =
            trackSelector
                .buildUponParameters()
                .setSelectionOverride(type, trackGroupArray, override)
                .build()

    }
    private fun setUpPlayer() {
        val trackSelector = DefaultTrackSelector(getApplication() as Context)

        val kk = DefaultRenderersFactory(getApplication()).setExtensionRendererMode(
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        val simpleExoplayer = ExoPlayer.Builder(getApplication(), kk)
            .setTrackSelector(trackSelector)
            .build()
        _ts.value = trackSelector
        simpleExoplayer.setMediaItem(PlayData.currentMediaItem!!)
        simpleExoplayer.prepare()
        simpleExoplayer.playWhenReady = playWhenReady
        simpleExoplayer.seekTo(PlayData.currentPosition)
        simpleExoplayer.play()
        val lister = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                val latest = getMapOfTrackByType(C.TRACK_TYPE_TEXT, trackSelector).size-1
                val typeValue = getTypeIndex(trackSelector, C.TRACK_TYPE_TEXT)
                switchToDifferentTrack(trackSelector, typeValue, latest)
                simpleExoplayer.removeListener(this)
                setUpChosenAudioTrack()
            }
        }
        simpleExoplayer.addListener(lister)
        this._player.value = simpleExoplayer
    }
    fun releaseExoPlayer() {
        val player = _player.value ?: return
        this._player.value = null
        contentPosition = player.contentPosition
        playWhenReady = player.playWhenReady
        player.release()
    }
    private fun stopServer() {
        CoroutineScope(Dispatchers.IO).launch {
            if (CurrentServer.server != null) {
                CurrentServer.server!!.stop()
            }
        }
    }
    fun savePositionAndStop() {
        if (_player.value!!.isPlaying) {
            PlayData.setPosition(_player.value?.currentPosition!!)
            _player.value!!.stop()
            stopServer()
        }
    }
    override fun onCleared() {
        releaseExoPlayer()
        super.onCleared()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(currentObserver)
    }
}

class FragmentNewPlayer : Fragment() {
    private lateinit var viewModel: PlayerViewModel
    private var _binding: FragmentNewPlayerBinding? = null
    private val binding get() = _binding!!
    private lateinit var fullScreenButton : ImageView
    private lateinit var playerView : PlayerView
    private var fullScreen = false
    private var isPlaying = true
    lateinit var toolbar: ToolBarHelper
    private val currentSub: CurrentSub by activityViewModels()
    private fun setScreenButtonsVisible(on: Boolean) {
        if (!on) {
            activity?.findViewById<ConstraintLayout>(R.id.buttonFieldVertical)?.visibility = View.VISIBLE
            activity?.findViewById<ImageButton>(R.id.exo_audio_track)?.visibility = View.VISIBLE
            activity?.findViewById<ImageButton>(R.id.exo_changezoom)?.visibility = View.VISIBLE
        } else {
            activity?.findViewById<ConstraintLayout>(R.id.buttonFieldVertical)?.visibility = View.GONE
            activity?.findViewById<ImageButton>(R.id.exo_audio_track)?.visibility = View.GONE
            activity?.findViewById<ImageButton>(R.id.exo_changezoom)?.visibility = View.GONE
        }
    }
    private fun sizeToWindow() {
        val newParams = playerView.layoutParams
        val screenWidth = activity?.windowManager?.defaultDisplay?.width
        if (screenWidth != null) {
            val newHeight = (screenWidth / 16) * 9
            newParams.height = newHeight
            playerView.layoutParams = newParams
        }
    }
    private fun activateFullScreen() {
        if (playerView.player?.isPlaying != null) {
            isPlaying = playerView.player?.isPlaying!!
        }
        val drawable = AppCompatResources.getDrawable(requireContext(), com.google.android.exoplayer2.R.drawable.exo_ic_fullscreen_exit)
        fullScreenButton.setImageDrawable(drawable)
        hideSystemUI()
        activity?.findViewById<ConstraintLayout>(R.id.buttonFieldVertical)?.visibility = View.VISIBLE
        val params = playerView.layoutParams
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        params.width = LinearLayout.LayoutParams.MATCH_PARENT
        params.height = LinearLayout.LayoutParams.MATCH_PARENT
        playerView.showController()
        setScreenButtonsVisible(false)
        playerView.hideController()
        fullScreen = true
        toolbar.hideToolbarCompletely()
    }
    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(activity?.window!!, false)
        WindowInsetsControllerCompat(activity?.window!!, binding.exoplayerFullscreen).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    private fun deactivateFullScreen() {
        if (playerView.player?.isPlaying != null) {
            isPlaying = playerView.player?.isPlaying!!
        }
        val drawable = AppCompatResources.getDrawable(requireContext(), com.google.android.exoplayer2.R.drawable.exo_ic_fullscreen_enter)
        fullScreenButton.setImageDrawable(drawable)
        val params = playerView.layoutParams
        params.width = LinearLayout.LayoutParams.MATCH_PARENT
        params.height = (200 * resources.displayMetrics.density).toInt()
        playerView.showController()
        setScreenButtonsVisible(true)
        playerView.hideController()
        fullScreen = false
        sizeToWindow()
        toolbar.showToolbarCompletely()

    }
    private fun onConfigChanged(newConfig : Int) {
        if (newConfig == Configuration.ORIENTATION_LANDSCAPE) {
            activateFullScreen()
        } else if (newConfig == Configuration.ORIENTATION_PORTRAIT) {
            deactivateFullScreen()
        }
    }
    private fun createMediaItemWithSSL() {
        val trustStore = KeyStore.getInstance("BKS")
        val keyStoreStream = activity?.assets?.open("keystore1.bks")
        trustStore.load(keyStoreStream, null)
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(trustStore)
        val sslCtx = SSLContext.getInstance("TLS")
        sslCtx.init(null, tmf.trustManagers, java.security.SecureRandom())
        HttpsURLConnection.setDefaultSSLSocketFactory(sslCtx.socketFactory)
        HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
    }
    private fun getMedia(): MediaItem {
        return when (currentSub.videoLocatedAt) {
            CurrentSub.LOCAL    -> { MediaItem.fromUri(currentSub.videoLocation as Uri) }
            CurrentSub.LAN      -> { createMediaItemWithSSL()
                                     MediaItem.fromUri("https://127.0.0.1:${CurrentServer.designatedPort}") }
            CurrentSub.INTERNET -> { MediaItem.fromUri(currentSub.videoLocation as String)}
            else                ->   MediaItem.EMPTY
        }
    }
    private fun setSubtitleView() {
        playerView.subtitleView?.setStyle(
            CaptionStyleCompat(
                Color.WHITE,
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                Color.BLACK,
                null
            )
        )
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewPlayerBinding.inflate(inflater, container, false)
        val view = binding.root
        playerView = binding.exoplayerFullscreen
        setSubtitleView()
        val other = getMedia()
        val location = currentSub.subtitle.saveTempFile(requireContext())
        val tempSubtitle = MediaItem.SubtitleConfiguration
            .Builder(Uri.parse(location))
            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
            .setLanguage("nl")
            .build()
        val mediaWithSub = other.buildUpon().setSubtitleConfigurations(listOf(tempSubtitle)).build()
        PlayData.setCurrentMedia(mediaWithSub)
        toolbar = ToolBarHelper()
        toolbar.getToolbar(this)
        toolbar.hideItems(listOf(R.id.view_toolbar, R.id.play_toolbar))
        toolbar.addDescription(getString(R.string.FragmentNewPlayer))
        viewModel = ViewModelProvider(this)[PlayerViewModel::class.java]
        viewModel.player.observe(viewLifecycleOwner, Observer {
            playerView.player = it
            fullScreenButton = activity?.findViewById(R.id.exoplayer_fullscreen_icon)!!
            it?.addListener(PlayerEventListener(playerView))
            onConfigChanged(resources.configuration.orientation)
            initializeButtons()
            updateText()
        })
        findNavController().addOnDestinationChangedListener(NavigationListener(viewModel))
        sizeToWindow()
        return view
    }
    private fun chooseSubtitle(typeAsked: Int) {
        val myFragment = FragmentEdit()
        myFragment.receive(typeAsked, currentSub.subtitle, this)
        childFragmentManager
           .beginTransaction()
           .add(binding.buttonField.id, myFragment)
           .addToBackStack(null)
           .commit()
        updateText()
    }
    fun updateText() {
        binding.firstSelectedSub.text = currentSub.subtitle.getLine(ReceivedInfo.getValue(ReceivedInfo.PLAY_DATA_FIRST)).toSpannable()
        binding.lastSelectedSub.text  = currentSub.subtitle.getLine(ReceivedInfo.getValue(ReceivedInfo.PLAY_DATA_LAST)).toSpannable()
    }
    private fun confirmSync(startOrEnd: Boolean, simpleExoplayer: ExoPlayer) {
        val confirmSyncDialog = ConfirmSyncDialog()
        confirmSyncDialog.startOrEnd = startOrEnd
        confirmSyncDialog.cur = simpleExoplayer.currentPosition.toInt()
        confirmSyncDialog.show(childFragmentManager, "")
        updateText()
    }
    private fun jumpToGivenPoint(positionInMills : Long) {
        viewModel.jumpToAGivenPoint(positionInMills)
    }
    private fun initializeButtons() {
        currentSub.subtitle.subtitleChange.observe(viewLifecycleOwner, Observer {
            updateText()
            currentSub.backupSession(this, it)
        })
        binding.back100millisecond.setOnClickListener {
            val point = viewModel.player.value?.currentPosition as Long
            jumpToGivenPoint(point - 100)
        }
        binding.back1second.setOnClickListener {
            val point = viewModel.player.value?.currentPosition as Long
            jumpToGivenPoint(point - 1000)
        }
        binding.forward1second.setOnClickListener {
            val point = viewModel.player.value?.currentPosition as Long
            jumpToGivenPoint(point + 1000)
        }
        binding.forward100millisecond.setOnClickListener {
            val point = viewModel.player.value?.currentPosition as Long
            jumpToGivenPoint(point + 100)
        }
        binding.selectFirstSubPlayer.setOnClickListener {
            chooseSubtitle(22)
        }
        binding.selectLastSubPlayer.setOnClickListener {
            chooseSubtitle(23)
        }
        binding.setTimeAsStart.setOnClickListener {
            confirmSync(true, playerView.player as ExoPlayer)
        }
        binding.setTimeAsEnd.setOnClickListener {
            confirmSync(false, playerView.player as ExoPlayer)
        }
        binding.reloadButton.setOnClickListener {
            val location = currentSub.subtitle.saveTempFile(requireContext())
            viewModel.reloadSubtitle(location)
        }
        activity?.findViewById<ImageButton>(R.id.exo_changezoom)?.setOnClickListener {
            val allAspectRatios = listOf(AspectRatioFrameLayout.RESIZE_MODE_FILL,
                AspectRatioFrameLayout.RESIZE_MODE_FIT,
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT,
                AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH)
            var current = allAspectRatios.indexOf(playerView.resizeMode)
            if (current == allAspectRatios.size-1) {
                current = -1
            }
            playerView.resizeMode = allAspectRatios[current+1]
        }
        activity?.findViewById<ImageButton>(R.id.exo_audio_track)?.setOnClickListener {
            val description = viewModel.cycleThroughAudio()
            val info = getString(R.string.switched_to_audio_track) + " ${PlayData.currentAudioTrack+1}  $description"
            Toast.makeText(requireContext(), info, Toast.LENGTH_SHORT).show()
        }
        binding.switchAudio.setOnClickListener {
            val description = viewModel.cycleThroughAudio()
            val info = getString(R.string.switched_to_audio_track) + " ${PlayData.currentAudioTrack+1}  $description"
            Toast.makeText(requireContext(), info, Toast.LENGTH_SHORT).show()
        }
        binding.pause.setOnClickListener {
            if (viewModel.player.value?.isPlaying!!) {
                viewModel.player.value?.pause()
            } else {
                viewModel.player.value?.play()
            }
        }
        binding.jumpToStart.setOnClickListener {
            val start = currentSub.subtitle.getLine(ReceivedInfo.getValue(ReceivedInfo.PLAY_DATA_FIRST)).startTime
            jumpToGivenPoint(start.toLong())
        }
        binding.jumpToEnd.setOnClickListener {
            val end = currentSub.subtitle.getLine(ReceivedInfo.getValue(ReceivedInfo.PLAY_DATA_LAST)).startTime
            jumpToGivenPoint(end.toLong())
        }
        activity?.findViewById<TextView>(R.id.setAsStartingPlayer)?.setOnClickListener {
            confirmSync(true, playerView.player as ExoPlayer)
        }
        activity?.findViewById<TextView>(R.id.setAsEndingPlayer)?.setOnClickListener {
            confirmSync(false, playerView.player as ExoPlayer)
        }
        activity?.findViewById<TextView>(R.id.reloadButtonPlayer)?.setOnClickListener {
            val location = currentSub.subtitle.saveTempFile(requireContext())
            viewModel.reloadSubtitle(location)
        }

        activity?.findViewById<TextView>(R.id.jumpToTheEndPlayer)?.setOnClickListener {
            val end = currentSub.subtitle.getLine(ReceivedInfo.getValue(ReceivedInfo.PLAY_DATA_LAST)).startTime
            jumpToGivenPoint(end.toLong())
        }
        activity?.findViewById<TextView>(R.id.jumpToTheStartPlayer)?.setOnClickListener {
            val start = currentSub.subtitle.getLine(ReceivedInfo.getValue(ReceivedInfo.PLAY_DATA_FIRST)).startTime
            jumpToGivenPoint(start.toLong())
        }
        fullScreenButton.setOnClickListener {
            if (fullScreen && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                deactivateFullScreen()
            }else if (!fullScreen) {
                activateFullScreen()
            } else {
                deactivateFullScreen()
            }
        }
        currentSub.backupPlayData(this)
    }

    override fun onPause() {
        if (viewModel.player.value?.currentPosition != null) {
            PlayData.currentPosition = viewModel.player.value?.currentPosition!!
        }
        currentSub.backupPlayData(this)
        super.onPause()
    }

    override fun onStop() {
        if (viewModel.player.value?.currentPosition != null) {
            PlayData.currentPosition = viewModel.player.value?.currentPosition!!
        }
        currentSub.backupPlayData(this)
        super.onStop()
    }
    override fun onResume() {
        super.onResume()
        if (isPlaying) {
            viewModel.player.value?.play()
        }
        toolbar.showToolbar()
    }
    override fun onDetach() {
        if (viewModel.player.value?.currentPosition != null) {
            PlayData.currentPosition = viewModel.player.value?.currentPosition!!
        }
        currentSub.backupPlayData(this)
        if (this::toolbar.isInitialized) {
            toolbar.hideToolbar()
        }
        super.onDetach()
    }

    override fun onDestroyView() {
        if (viewModel.player.value?.currentPosition != null) {
            PlayData.currentPosition = viewModel.player.value?.currentPosition!!
        }
        currentSub.backupPlayData(this)
        super.onDestroyView()
    }
}