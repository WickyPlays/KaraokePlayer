package me.wickyplays.android.karaokeplayer.player.processors

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import me.wickyplays.android.karaokeplayer.App
import me.wickyplays.android.karaokeplayer.player.obj.Song
import java.io.IOException
import androidx.core.net.toUri
import me.wickyplays.android.karaokeplayer.player.KaraokePlayerCore
import me.wickyplays.android.karaokeplayer.player.interfaces.KaraokePlayerProcessor
import java.util.concurrent.TimeUnit

class KaraokeMidiKarProcessor : KaraokePlayerProcessor() {
    private var mediaPlayer: MediaPlayer? = null
    private var currentSong: Song? = null
    private var isPaused = false
    private var isStopped = false
    private var playbackSpeed = 1.0
    private var handler: Handler? = null
    private var updateLyricTask: Runnable? = null
    private var core = KaraokePlayerCore.instance
    private var lastLyricUpdateTime: Double = 0.0
    private var lastMetaUpdateTime: Double = 0.0

    private val lyricUpdateInterval = 0.016
    private val metaUpdateInterval = 0.5

    override fun processSong(song: Song): Song {
        currentSong = song
        return song
    }

    override fun start() {
        currentSong?.let { song ->
            try {
                mediaPlayer?.release()
                Log.d("Player", "Starting media player...")
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(
                        App.context!!,
                        song.songPath.toUri()
                    )
                    prepareAsync()
                    setOnPreparedListener { mp ->
                        mp.start()
                        isPaused = false
                        isStopped = false
                        lastLyricUpdateTime = 0.0
                        lastMetaUpdateTime = 0.0
                        startLyricUpdate()
                    }
                    setOnCompletionListener {
                        stop()
                        core.onSongCompleted()
                    }
                    setOnErrorListener { mp, what, extra ->
                        Log.e("MediaPlayer", "Error occurred: what=$what, extra=$extra")
                        stop()
                        core.onSongError()
                        true
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                cleanup()
                core.onSongError()
            }
        }
    }

    private fun startLyricUpdate() {
        handler = Handler(Looper.getMainLooper())
        updateLyricTask = object : Runnable {
            override fun run() {
                val currentTime = getCurrentPlaybackTime()

                // Update lyrics only when needed (based on time interval)
                if (currentTime >= lastLyricUpdateTime + lyricUpdateInterval) {
                    core.getLyricManager().updateLyrics(currentTime)
                    core.getJudgementManager().updateJudgement(currentTime)
                    lastLyricUpdateTime = currentTime
                }

                // Update metadata less frequently
                if (currentTime >= lastMetaUpdateTime + metaUpdateInterval) {
                    updateSongMeta(currentTime)
                    lastMetaUpdateTime = currentTime
                }

                handler?.post(this)
            }
        }
        updateLyricTask?.let { handler?.post(it) }
    }

    private fun updateSongMeta(currentTime: Double) {
        if (currentSong != null) {
            val totalTime = getTotalTime()
            val currentTimeFormatted = formatTime(currentTime)
            val totalTimeFormatted = formatTime(totalTime)
            core.getSongQueueManager().setSongMetaText(
                "Đang phát: ${currentSong!!.title} ($currentTimeFormatted/$totalTimeFormatted)"
            )
        }
    }

    private fun formatTime(seconds: Double): String {
        val millis = (seconds * 1000).toLong()
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val remainingSeconds = TimeUnit.MILLISECONDS.toSeconds(millis) -
                TimeUnit.MINUTES.toSeconds(minutes)
        return String.format("%02d:%02d", minutes, remainingSeconds)
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
                stopLyricUpdate()
            }
        }
    }

    override fun resume() {
        mediaPlayer?.let {
            if (!it.isPlaying && isPaused) {
                it.start()
                isPaused = false
                startLyricUpdate()
            }
        }
    }

    override fun stop() {
        mediaPlayer?.let {
            if (it.isPlaying || isPaused) {
                it.stop()
            }
        }
        cleanup()
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
        stopLyricUpdate()
    }

    override fun isRunning(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }

    override fun isPausedState(): Boolean {
        return isPaused
    }
}