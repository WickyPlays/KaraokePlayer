package me.wickyplays.android.karaokeplayer.player

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.net.toUri
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import me.wickyplays.android.karaokeplayer.R
import me.wickyplays.android.karaokeplayer.databinding.ActivityPlayerBinding
import me.wickyplays.android.karaokeplayer.player.interfaces.KaraokePlayerProcessor
import me.wickyplays.android.karaokeplayer.player.manager.PlayerDirectoryManager
import me.wickyplays.android.karaokeplayer.player.manager.PlayerLoadingManager
import me.wickyplays.android.karaokeplayer.player.manager.PlayerLyricManager
import me.wickyplays.android.karaokeplayer.player.obj.Song
import me.wickyplays.android.karaokeplayer.player.processors.KaraokeMediaProcessor

class KaraokePlayerCore private constructor() {

    private val songList = mutableListOf<Song>()
    private val queueSongList = mutableListOf<Song>()
    private var currentSong: Song? = null
    private lateinit var binding: ActivityPlayerBinding
    private lateinit var context: Context
    private val digits = Array(6) { '0' }
    private var foundSong: Song? = null
    private val loadingDelay = 1000L // 1 second delay
    private lateinit var karaokeProcessor: KaraokePlayerProcessor

    private lateinit var loadingManager: PlayerLoadingManager
    private lateinit var lyricManager: PlayerLyricManager
    private lateinit var directoryManager: PlayerDirectoryManager

    companion object {
        val instance: KaraokePlayerCore by lazy {
            KaraokePlayerCore()
        }
    }

    fun initialize(context: Context, binding: ActivityPlayerBinding) {
        this.songList.clear()
        this.queueSongList.clear()

        this.context = context
        this.binding = binding

        loadingManager = PlayerLoadingManager(context, binding)
        lyricManager = PlayerLyricManager(context, binding)
        directoryManager = PlayerDirectoryManager(context)

        setupBackgroundVideo()
        setupSongSelector();
        startFpsCounter()
        updatePlayerSongSelector()
        updatePlayerQueueBar()

        loadingManager.setLoadingState(true, "Initializing song library")
        directoryManager.setup()

        Handler(Looper.getMainLooper()).postDelayed({
            loadingManager.setLoadingState(false)
        }, loadingDelay)
    }

    private fun setupBackgroundVideo() {
        binding.videoView.setMediaController(null)
        binding.videoView.setVideoURI("android.resource://${context.packageName}/${R.raw.karaokebg}".toUri())
        binding.videoView.setOnPreparedListener {
            it.isLooping = true
            it.setVolume(0f, 0f)
            binding.videoView.start()
        }
        binding.videoView.setOnErrorListener { mp, what, extra ->
            android.util.Log.e("VideoView", "Error occurred: what=$what, extra=$extra")
            true
        }
    }

    private fun setupSongSelector() {
        updateNumberDisplay()
        binding.songSelector.root.visibility = View.VISIBLE
    }

    fun handleKeyDown(keyCode: Int): Boolean {
        if (keyCode in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9) {
            binding.songSelector.root.visibility = View.VISIBLE
            val digit = (keyCode - android.view.KeyEvent.KEYCODE_0).toString()
            System.arraycopy(digits, 1, digits, 0, digits.size - 1)
            digits[digits.size - 1] = digit[0]
            updateNumberDisplay()

            val songNumber = digits.joinToString("")
            foundSong = getSongFromNumber(songNumber)
            updatePlayerSongSelector()

            return true
        } else if (keyCode in android.view.KeyEvent.KEYCODE_NUMPAD_0..android.view.KeyEvent.KEYCODE_NUMPAD_9) {
            binding.songSelector.root.visibility = View.VISIBLE
            val digit = (keyCode - android.view.KeyEvent.KEYCODE_NUMPAD_0).toString()
            System.arraycopy(digits, 1, digits, 0, digits.size - 1)
            digits[digits.size - 1] = digit[0]
            updateNumberDisplay()

            val songNumber = digits.joinToString("")
            foundSong = getSongFromNumber(songNumber)
            updatePlayerSongSelector()
            return true
        } else if (keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
            keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PLAY ||
            keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            foundSong?.let {
                android.util.Log.d("Player", "Selected song: ${it.number} - ${it.title}")
                addSongToQueue(it)
            }
            digits.fill('0')
            updateNumberDisplay()
            updatePlayerQueueBar()

            return true
        } else if (keyCode == android.view.KeyEvent.KEYCODE_N || keyCode == android.view.KeyEvent.KEYCODE_MEDIA_NEXT) {
            skipToNextSong()
            updatePlayerQueueBar()
            return true
        }
        return false
    }

    private fun startFpsCounter() {
        val fpsView = binding.playerFpsView
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var frameCount = 0
        var lastUpdateTime = System.currentTimeMillis()
        val fpsUpdateInterval = 1000L

        val updateFps = object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                frameCount++
                if (currentTime - lastUpdateTime >= fpsUpdateInterval) {
                    val fps = (frameCount * 1000.0 / (currentTime - lastUpdateTime)).toInt()
                    fpsView.text = "FPS: $fps"
                    frameCount = 0
                    lastUpdateTime = currentTime
                }
                binding.videoView.invalidate()
                handler.post(this)
            }
        }
        handler.post(updateFps)
    }

    private fun skipToNextSong() {
        if (queueSongList.isNotEmpty()) {
            currentSong = null;
            queueSongList.removeAt(0)
            if (!queueSongList.isEmpty()) {
                playSong(queueSongList[0])
            }
            updatePlayerQueueBar()
        }
    }

    private fun updateNumberDisplay() {
        binding.songSelector.number.text = digits.joinToString("")
    }

    private fun updatePlayerQueueBar() {
        val playerQueueBar = binding.playerQueueBar
        playerQueueBar.queueBar.removeAllViews()

        if (queueSongList.isEmpty()) {
            playerQueueBar.root.visibility = View.INVISIBLE
        } else {
            playerQueueBar.root.visibility = View.VISIBLE
        }

        queueSongList.forEachIndexed { index, song ->
            val textView = TextView(context).apply {
                text = song.number
                textSize = 20f
                when (index) {
                    0 -> setTextColor(Color.GREEN)
                    1 -> setTextColor(Color.YELLOW)
                    else -> setTextColor(Color.WHITE)
                }

                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 8
                }
                this.layoutParams = layoutParams
            }
            playerQueueBar.queueBar.addView(textView)
        }
    }

    private fun updatePlayerSongSelector() {
        val playerSongSelector = binding.songSelector
        playerSongSelector.title.visibility = foundSong?.let { View.VISIBLE } ?: View.GONE
        playerSongSelector.title.text = foundSong?.title
    }

    fun playSong(song: Song?) {
        currentSong = song
        lyricManager.initLyricFromSong(song!!)
        karaokeProcessor = KaraokeMediaProcessor()
        karaokeProcessor.processSong(currentSong!!)
        karaokeProcessor.start()
    }

    fun addSong(song: Song) {
        songList.add(song)
    }

    fun addSongToQueue(song: Song) {
        queueSongList.add(song)

        if (queueSongList.size == 1) {
            playSong(queueSongList[0])
        }
    }

    fun getSongFromNumber(number: String): Song? {
        return songList.find { it.number == number }
    }

    fun getSongList(): List<Song> {
        return songList
    }

    fun getQueueSongList(): List<Song> {
        return queueSongList
    }

    fun getCurrentSong(): Song? {
        return currentSong
    }

    fun nextSong(): Song? {
        if (queueSongList.isEmpty()) return null
        currentSong = queueSongList.removeAt(0)
        return currentSong
    }

    fun getLoadingManager(): PlayerLoadingManager {
        return loadingManager
    }

    fun getLyricManager(): PlayerLyricManager {
        return lyricManager
    }

}