package me.wickyplays.android.karaokeplayer.player.interfaces

import me.wickyplays.android.karaokeplayer.player.obj.Song

abstract class KaraokePlayerProcessor {
    abstract fun processSong(song: Song): Song

    abstract fun start()
    abstract fun pause()
    abstract fun resume()
    abstract fun stop()

    abstract fun getSpeed(): Double
    abstract fun setSpeed(speed: Double)

    abstract fun getCurrentPlaybackTime(): Double
    abstract fun getTotalTime(): Double

    abstract fun cleanup()

    abstract fun isRunning(): Boolean
    abstract fun isPausedState(): Boolean
}