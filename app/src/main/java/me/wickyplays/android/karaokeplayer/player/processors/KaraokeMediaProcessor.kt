package me.wickyplays.android.karaokeplayer.player.processors

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import me.wickyplays.android.karaokeplayer.App
import me.wickyplays.android.karaokeplayer.player.obj.Song
import java.io.IOException
import androidx.core.net.toUri
import me.wickyplays.android.karaokeplayer.R
import me.wickyplays.android.karaokeplayer.player.KaraokePlayerCore
import me.wickyplays.android.karaokeplayer.player.interfaces.KaraokePlayerProcessor

class KaraokeMediaProcessor : KaraokePlayerProcessor() {
    private var mediaPlayer: MediaPlayer? = null
    private var currentSong: Song? = null
    private var isPaused = false
    private var isStopped = false
    private var playbackSpeed = 1.0
    private var handler: Handler? = null
    private var updateLyricTask: Runnable? = null
    private var core = KaraokePlayerCore.instance

    override fun processSong(song: Song): Song {
        currentSong = song
        return song
    }

    override fun start() {
        currentSong?.let { song ->
            try {
                mediaPlayer?.release()
                Log.d("Player", "Starting media player...")
                val packageName = App.context?.packageName
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(App.context!!,
                        "android.resource://${packageName}/${R.raw.sample}".toUri())
                    prepareAsync()
                    setOnPreparedListener { mp ->
                        mp.start()
                        isPaused = false
                        isStopped = false
                    }
                    setOnCompletionListener {
                        stop(false)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                cleanup()
            }
        }

        startLyricUpdate()
    }

    private fun startLyricUpdate() {
        handler = Handler(Looper.getMainLooper())
        val frameDuration = 1000L / 60 // ~16.67ms for 60 FPS
        updateLyricTask = object : Runnable {
            override fun run() {
                core.getLyricManager().updateLyrics(getCurrentPlaybackTime())
                handler?.postDelayed(this, frameDuration)
            }
        }
        updateLyricTask?.let { handler?.post(it) }
    }

    private fun stopLyricUpdate() {
        updateLyricTask?.let { handler?.removeCallbacks(it) }
        handler = null
        updateLyricTask = null
    }

    override fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isPaused = true
            }
        }
    }

    override fun resume() {
        mediaPlayer?.let {
            if (!it.isPlaying && isPaused) {
                it.start()
                isPaused = false
            }
        }
    }

    override fun stop(interrupted: Boolean) {
        mediaPlayer?.let {
            if (it.isPlaying || isPaused) {
                it.stop()
                isStopped = true
                isPaused = false
            }
        }

        stopLyricUpdate()
    }

    override fun getSpeed(): Double = playbackSpeed

    override fun setSpeed(speed: Double) {
        playbackSpeed = speed
        mediaPlayer?.let {
            it.playbackParams = it.playbackParams.apply {
                setSpeed(speed.toFloat())
            }
        }
    }

    override fun getCurrentPlaybackTime(): Double {
        return (mediaPlayer?.currentPosition?.toDouble() ?: 0.0) / 1000
    }

    override fun getTotalTime(): Double {
        return (mediaPlayer?.duration ?: 0).toDouble() / 1000
    }

    override fun cleanup() {
        mediaPlayer?.release()
        mediaPlayer = null
        currentSong = null
        isPaused = false
        isStopped = true
    }

    override fun isRunning(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }

    override fun isPausedState(): Boolean {
        return isPaused
    }
}