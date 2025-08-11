package me.wickyplays.android.karaokeplayer.cores.player

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
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
import me.wickyplays.android.karaokeplayer.cores.player.enums.SongType
import me.wickyplays.android.karaokeplayer.cores.player.manager.PlayerDirectoryManager
import me.wickyplays.android.karaokeplayer.cores.player.manager.PlayerJudgementManager
import me.wickyplays.android.karaokeplayer.cores.player.manager.PlayerLoadingManager
import me.wickyplays.android.karaokeplayer.cores.player.manager.PlayerLyricManager
import me.wickyplays.android.karaokeplayer.cores.player.manager.PlayerScoreManager
import me.wickyplays.android.karaokeplayer.cores.player.manager.PlayerSongMenuManager
import me.wickyplays.android.karaokeplayer.cores.player.manager.PlayerSongQueueManager
import me.wickyplays.android.karaokeplayer.cores.player.obj.Song
import me.wickyplays.android.karaokeplayer.cores.player.processors.KaraokeMediaProcessor
import me.wickyplays.android.karaokeplayer.databinding.ActivityPlayerBinding

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
    private val scoreDisplayDelay = 10000L
    private var scoreHideRunnable: Runnable? = null
    private var songSelectorHideRunnable: Runnable? = null

    // Managers
    private lateinit var songQueueManager: PlayerSongQueueManager
    private lateinit var loadingManager: PlayerLoadingManager
    private lateinit var lyricManager: PlayerLyricManager
    private lateinit var directoryManager: PlayerDirectoryManager
    private lateinit var scoreManager: PlayerScoreManager
    private lateinit var judgementManager: PlayerJudgementManager
    private lateinit var songMenuManager: PlayerSongMenuManager

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
        songMenuManager = PlayerSongMenuManager(context, binding).apply {
            initialize(songList) { song -> addSongToQueue(song) }
        }

        setupBackgroundVideo()
        setupSongSelector()
        startFpsCounter()
        updatePlayerSongSelector()
        updatePlayerQueueBar()

        loadingManager.setLoadingState(true, "Initializing song library")
        directoryManager.setup()

        Handler(Looper.getMainLooper()).postDelayed({
            loadingManager.setLoadingState(false)
            updateSongSelectorVisibility()
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
        showSongSelectorVisible(true)
    }

    fun handleKeyDown(keyCode: Int): Boolean {
        Log.d("Player", "Key code pressed: $keyCode")

        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            showSongSelectorVisible(true)
            val digit = (keyCode - KeyEvent.KEYCODE_0).toString()
            System.arraycopy(digits, 1, digits, 0, digits.size - 1)
            digits[digits.size - 1] = digit[0]
            updateNumberDisplay()

            val songNumber = digits.joinToString("")
            foundSong = getSongFromNumber(songNumber)
            updatePlayerSongSelector()

            resetSongSelectorHideTimer()

            return true
        } else if (keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9) {
            showSongSelectorVisible(true)
            val digit = (keyCode - KeyEvent.KEYCODE_NUMPAD_0).toString()
            System.arraycopy(digits, 1, digits, 0, digits.size - 1)
            digits[digits.size - 1] = digit[0]
            updateNumberDisplay()

            val songNumber = digits.joinToString("")
            foundSong = getSongFromNumber(songNumber)
            updatePlayerSongSelector()

            resetSongSelectorHideTimer()

            return true
        } else if (keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        ) {
            foundSong?.let {
                Log.d("Player", "Selected song: ${it.number} - ${it.title}")
                addSongToQueue(it)
                digits.fill('0')
                foundSong = null
                updateNumberDisplay()
                updatePlayerSongSelector()
                showSongSelectorVisible(false)
            }
            updatePlayerQueueBar()

            songSelectorHideRunnable?.let { Handler(Looper.getMainLooper()).removeCallbacks(it) }
            songSelectorHideRunnable = null

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
        } else if (keyCode == KeyEvent.KEYCODE_MENU) {
            songMenuManager.toggleMenuVisibility()
            return true
        }
        return false
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

    fun onSongCompleted() {
        showScore(getJudgementManager().score)
    }

    fun onSongError() {
        skipToNextSong()
    }

    fun showScore(score: Int) {
        scoreManager.setScoreVisible(true)
        AudioManager.getInstance(context).playScoreSoundEffect()
        scoreManager.setScoreText(score)

        scoreHideRunnable?.let { Handler(Looper.getMainLooper()).removeCallbacks(it) }
        scoreHideRunnable = Runnable {
            scoreManager.setScoreVisible(false)
            skipToNextSong()
        }
        scoreHideRunnable?.let {
            Handler(Looper.getMainLooper()).postDelayed(it, scoreDisplayDelay)
        }
    }

    fun showSongSelectorVisible(visible: Boolean) {
        binding.songSelector.root.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun skipToNextSong() {
        karaokeProcessor?.stop()
        lyricManager.clearLyricViews()
        currentSong = null

        if (queueSongList.isNotEmpty()) {
            queueSongList.removeAt(0)
            if (queueSongList.isNotEmpty()) {
                playSong(queueSongList[0])
            }
            updatePlayerQueueBar()
        }

        updateSongSelectorVisibility()
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

        karaokeProcessor?.stop()
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

        if (song.songType == SongType.MIDI) {
            karaokeProcessor = KaraokeMediaProcessor()
        } else if (song.songType == SongType.AUDIO) {
            karaokeProcessor = KaraokeMediaProcessor()
        } else {
            karaokeProcessor = null
        }

        showSongSelectorVisible(false)

        if (karaokeProcessor == null) {
            skipToNextSong()
            return
        }
        karaokeProcessor?.processSong(currentSong!!)
        karaokeProcessor?.start()

    }

    fun addSong(song: Song) {
        songList.add(song)
        songMenuManager.addSong(song)
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

    private fun updateSongSelectorVisibility() {
        val shouldBeVisible = queueSongList.isEmpty() || karaokeProcessor?.isRunning() != true
        showSongSelectorVisible(shouldBeVisible)
    }

    private fun resetSongSelectorHideTimer() {
        // Remove any existing callbacks first
        songSelectorHideRunnable?.let {
            Handler(Looper.getMainLooper()).removeCallbacks(it)
        }

        if (karaokeProcessor?.isRunning() == true) {
            showSongSelectorVisible(true)
            songSelectorHideRunnable = Runnable {
                showSongSelectorVisible(false)
            }
            // Schedule new runnable
            Handler(Looper.getMainLooper()).postDelayed(
                songSelectorHideRunnable!!,
                5000L
            )
        } else {
            songSelectorHideRunnable = null
        }
    }

    fun cleanup() {
        karaokeProcessor?.stop()
        lyricManager.clearLyricViews()
        scoreManager.setScoreVisible(false)
        judgementManager.stopPitchDetection()
        currentSong = null
        queueSongList.clear()
        songList.clear()
        foundSong = null

        scoreHideRunnable?.let {
            Handler(Looper.getMainLooper()).removeCallbacks(it)
            scoreHideRunnable = null
        }

        songSelectorHideRunnable?.let {
            Handler(Looper.getMainLooper()).removeCallbacks(it)
            songSelectorHideRunnable = null
        }

        showSongSelectorVisible(true)
    }
}