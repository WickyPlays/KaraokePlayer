package me.wickyplays.android.karaokeplayer.player

import android.content.Context
import android.media.MediaPlayer
import me.wickyplays.android.karaokeplayer.R

class AudioManager private constructor(context: Context) {
    private val homeMusicPlayer: MediaPlayer = MediaPlayer.create(context, R.raw.home_menu)
    private var soundEffectPlayer: MediaPlayer? = null
    private val appContext: Context = context.applicationContext

    companion object {
        @Volatile
        private var instance: AudioManager? = null

        fun getInstance(context: Context): AudioManager {
            return instance ?: synchronized(this) {
                instance ?: AudioManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun playHomeMusic() {
        if (!homeMusicPlayer.isPlaying) {
            homeMusicPlayer.isLooping = true
            homeMusicPlayer.start()
        }
    }

    fun stopHomeMusic() {
        if (homeMusicPlayer.isPlaying) {
            homeMusicPlayer.pause()
            homeMusicPlayer.seekTo(0)
        }
    }

    fun playSoundEffect(soundResId: Int) {
        soundEffectPlayer?.release() // Release previous player if exists
        soundEffectPlayer = MediaPlayer.create(appContext, soundResId).apply {
            setOnCompletionListener {
                release() // Release after playback completes
                soundEffectPlayer = null
            }
            start()
        }
    }
}