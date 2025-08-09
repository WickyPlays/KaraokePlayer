package me.wickyplays.android.karaokeplayer.player

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import me.wickyplays.android.karaokeplayer.R
import me.wickyplays.android.karaokeplayer.activities.HomeActivity
import me.wickyplays.android.karaokeplayer.databinding.ActivityPlayerBinding
import me.wickyplays.android.karaokeplayer.player.manager.PlayerDirectoryManager
import me.wickyplays.android.karaokeplayer.player.manager.PlayerJudgementManager
import me.wickyplays.android.karaokeplayer.player.manager.PlayerLoadingManager
import me.wickyplays.android.karaokeplayer.player.manager.PlayerLyricManager
import me.wickyplays.android.karaokeplayer.player.manager.PlayerScoreManager
import me.wickyplays.android.karaokeplayer.player.manager.PlayerSongQueueManager
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
    private val loadingDelay = 1000L
    private var karaokeProcessor: KaraokeMediaProcessor? = null
    private val selectorHideDelay = 5000L
    private val scoreDisplayDelay = 10000L
    private val selectorHandler = Handler(Looper.getMainLooper())
    private var selectorHideRunnable: Runnable? = null
    private var scoreHideRunnable: Runnable? = null

    private lateinit var songQueueManager: PlayerSongQueueManager
    private lateinit var loadingManager: PlayerLoadingManager
    private lateinit var lyricManager: PlayerLyricManager
    private lateinit var directoryManager: PlayerDirectoryManager
    private lateinit var scoreManager: PlayerScoreManager
    private lateinit var judgementManager: PlayerJudgementManager

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
        songQueueManager = PlayerSongQueueManager(binding)
        scoreManager = PlayerScoreManager(binding)
        judgementManager = PlayerJudgementManager(binding)

        setupBackgroundVideo()
        setupSongSelector()
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
        val uri = directoryManager.getBackgroundFromBgDir()
            ?: "android.resource://${context.packageName}/${R.raw.karaokebg}".toUri()
        binding.videoView.setMediaController(null)
        binding.videoView.setVideoURI(uri)
        binding.videoView.setOnPreparedListener {
            it.isLooping = true
            it.setVolume(0f, 0f)
            binding.videoView.start()
        }
        binding.videoView.setOnErrorListener { mp, what, extra ->
            Log.e("VideoView", "Error occurred: what=$what, extra=$extra")
            true
        }
    }

    private fun setupSongSelector() {
        updateNumberDisplay()
        setSongSelectorVisible(true)
    }

    fun handleKeyDown(keyCode: Int): Boolean {
        Log.d("Player", "Key code pressed: $keyCode")

        // Cancel any pending hide operation when a key is pressed
        selectorHideRunnable?.let {
            selectorHandler.removeCallbacks(it)
            selectorHideRunnable = null
        }

        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            setSongSelectorVisible(true)
            val digit = (keyCode - KeyEvent.KEYCODE_0).toString()
            System.arraycopy(digits, 1, digits, 0, digits.size - 1)
            digits[digits.size - 1] = digit[0]
            updateNumberDisplay()

            val songNumber = digits.joinToString("")
            foundSong = getSongFromNumber(songNumber)
            updatePlayerSongSelector()

            // Schedule hiding the selector after delay
            scheduleSelectorHide()
            return true
        } else if (keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9) {
            setSongSelectorVisible(true)
            val digit = (keyCode - KeyEvent.KEYCODE_NUMPAD_0).toString()
            System.arraycopy(digits, 1, digits, 0, digits.size - 1)
            digits[digits.size - 1] = digit[0]
            updateNumberDisplay()

            val songNumber = digits.joinToString("")
            foundSong = getSongFromNumber(songNumber)
            updatePlayerSongSelector()

            // Schedule hiding the selector after delay
            scheduleSelectorHide()
            return true
        } else if (keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        ) {
            foundSong?.let {
                Log.d("Player", "Selected song: ${it.number} - ${it.title}")
                addSongToQueue(it)
                setSongSelectorVisible(false)
            }
            updateNumberDisplay()
            updatePlayerQueueBar()

            return true
        } else if (keyCode == KeyEvent.KEYCODE_N || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
            skipToNextSong()
            updatePlayerQueueBar()
            return true
        } else if (keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_BACK) {
            cleanup()
            val intent = Intent(context, HomeActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
            return true
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_X) {
            if (karaokeProcessor != null) {
                val speed: Double = karaokeProcessor?.getSpeed() ?: 1.0
                karaokeProcessor?.setSpeed(if (speed == 1.0) 5.0 else 1.0)
                binding.playerQueueBar.metaSpeed.visibility =
                    if (speed == 1.0) View.VISIBLE else View.GONE
            }
        }
        return false
    }

    private fun scheduleSelectorHide() {
        selectorHideRunnable?.let { selectorHandler.removeCallbacks(it) }
        selectorHideRunnable = Runnable {
            if (karaokeProcessor?.isRunning() == true) {
                setSongSelectorVisible(false)
            }
        }
        selectorHideRunnable?.let {
            selectorHandler.postDelayed(it, selectorHideDelay)
        }
    }

    private fun startFpsCounter() {
        val fpsView = binding.playerFpsView
        val handler = Handler(Looper.getMainLooper())
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

    fun showScore(score: Int) {
        scoreManager.setScoreVisible(true)
        scoreManager.setScoreText(score)

        // Cancel any existing score hide operation
        scoreHideRunnable?.let { selectorHandler.removeCallbacks(it) }

        // Schedule hiding the score after 10 seconds
        scoreHideRunnable = Runnable {
            scoreManager.setScoreVisible(false)
            // Proceed to next song if available
            skipToNextSong()
        }
        scoreHideRunnable?.let {
            selectorHandler.postDelayed(it, scoreDisplayDelay)
        }
    }

    fun skipToNextSong() {
        karaokeProcessor?.stop(true)
        lyricManager.clearLyricViews()
        currentSong = null

        if (queueSongList.isNotEmpty()) {
            queueSongList.removeAt(0)
            if (queueSongList.isNotEmpty()) {
                playSong(queueSongList[0])
            }
            updatePlayerQueueBar()
        }

        if (karaokeProcessor?.isRunning() != true) {
            setSongSelectorVisible(true)
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
        if (song == null) return

        karaokeProcessor?.stop(true)
        lyricManager.clearLyricViews()

        currentSong = song
        lyricManager.initLyricFromSong(song)
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            judgementManager.initJudgementFromPath(song)
            judgementManager.startPitchDetection(context)
        }
        karaokeProcessor = KaraokeMediaProcessor()
        karaokeProcessor?.processSong(currentSong!!)
        karaokeProcessor?.start()

        setSongSelectorVisible(false)
    }

    fun addSong(song: Song) {
        songList.add(song)
    }

    fun addSongToQueue(song: Song) {
        queueSongList.add(song)

        if (queueSongList.size == 1) {
            playSong(queueSongList[0])
        }
        updatePlayerQueueBar()
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

    fun getSongQueueManager(): PlayerSongQueueManager {
        return songQueueManager
    }

    fun getScoreManager(): PlayerScoreManager {
        return scoreManager
    }

    fun getJudgementManager(): PlayerJudgementManager {
        return judgementManager
    }

    fun setSongSelectorVisible(visible: Boolean) {
        // Always show selector if no song is playing
        val shouldShow = visible || karaokeProcessor?.isRunning() != true
        binding.songSelector.root.visibility = if (shouldShow) View.VISIBLE else View.GONE

        // Cancel any pending hide operation when manually setting visibility
        if (!visible) {
            selectorHideRunnable?.let {
                selectorHandler.removeCallbacks(it)
                selectorHideRunnable = null
            }
        }
    }

    fun emptyLyric() {
        lyricManager.clearLyricViews()
    }

    fun cleanup() {
        karaokeProcessor?.stop(true)
        lyricManager.clearLyricViews()
        scoreManager.setScoreVisible(false)
        judgementManager.stopPitchDetection()
        currentSong = null
        queueSongList.clear()
        songList.clear()
        foundSong = null

        // Cancel any pending score hide operation
        scoreHideRunnable?.let {
            selectorHandler.removeCallbacks(it)
            scoreHideRunnable = null
        }

        // Show selector after cleanup
        setSongSelectorVisible(true)
    }
}