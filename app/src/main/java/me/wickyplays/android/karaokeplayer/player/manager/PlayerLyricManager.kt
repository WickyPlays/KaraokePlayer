package me.wickyplays.android.karaokeplayer.player.manager

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import me.wickyplays.android.karaokeplayer.databinding.ActivityPlayerBinding
import me.wickyplays.android.karaokeplayer.player.obj.LyricNode
import me.wickyplays.android.karaokeplayer.player.obj.Song
import java.io.File

class PlayerLyricManager(
    private val context: Context,
    private val binding: ActivityPlayerBinding
) {
    private lateinit var lyricGroups: List<List<LyricNode>>
    private lateinit var frames: List<LyricFrame>
    private var currentTopLineIndex: Int = -1
    private var currentBottomLineIndex: Int = -1
    private val gson = Gson()

    sealed class LyricFrameType {
        object TITLE_SHOW : LyricFrameType()
        object TITLE_HIDE : LyricFrameType()
        object COUNTDOWN : LyricFrameType()
        object LYRIC_TOP : LyricFrameType()
        object LYRIC_BOTTOM : LyricFrameType()
        object COOLDOWN_START : LyricFrameType()
        object COOLDOWN_END : LyricFrameType()
    }

    data class LyricFrame(
        val type: LyricFrameType,
        val time: Double,
        val lineIndex: Int? = null,
        val countdownFrom: Double? = null,
        val metadata: Metadata? = null,
        var available: Boolean = true
    )

    data class Metadata(
        val title: String,
        val artist: String? = null,
        val charter: String? = null,
        val lyricist: String? = null
    )

    fun initLyricFromSong(song: Song) {
        if (song.lyricPath.isNullOrBlank()) {
            frames = emptyList()
            lyricGroups = emptyList()
            setLyricTopView(null)
            setLyricBottomView(null)
            return
        }

        try {
            val lyricPath = song.lyricPath
            if (lyricPath != null) {
                Log.d("Player", "Reading lyric file: $lyricPath")
                val lyricFile = File(lyricPath)
                val lyricContent = lyricFile.readText()

                val type = object : TypeToken<List<List<LyricNode>>>() {}.type
                val groups: List<List<LyricNode>> = gson.fromJson(lyricContent, type)

                // Set metadata
                setMetadata(
                    Metadata(
                        title = song.title ?: "Unknown title",
                        artist = "Sáng tác: ${song.artist ?: "Unknown artist"}",
                        charter = "Tạo dựng: ${song.charter ?: "Unknown charter"}",
                        lyricist = "Lời hát: ${song.lyricist ?: "Unknown lyricist"}"
                    )
                )

                frames = createLyricFrames(song, groups)
                lyricGroups = groups
            }
        } catch (e: Exception) {
            Log.e("Player", "Error reading lyric file: ${e.message}")
            frames = emptyList()
            lyricGroups = emptyList()
            setLyricTopView(null)
            setLyricBottomView(null)
        }
    }

    fun updateLyrics(currTime: Double) {
        if (frames.isEmpty()) return

        // Handle frame triggers
        val passedFrames = frames.filter { it.time <= currTime }
        passedFrames.forEach {
            if (it.available) {
                it.available = false
                when (it.type) {
                    LyricFrameType.TITLE_SHOW -> {
                        setTitleViewVisible(true)
                        setLyricTopView(null)
                        setLyricBottomView(null)
                    }
                    LyricFrameType.TITLE_HIDE -> setTitleViewVisible(false)
                    LyricFrameType.COUNTDOWN -> setCountdownTime(it.countdownFrom ?: 0.0)
                    LyricFrameType.LYRIC_TOP -> {
                        currentTopLineIndex = it.lineIndex ?: -1
                        val group: List<LyricNode> = lyricGroups.getOrNull(currentTopLineIndex) ?: emptyList()
                        setLyricTopView(group.joinToString("") { node -> node.t })
                    }
                    LyricFrameType.LYRIC_BOTTOM -> {
                        currentBottomLineIndex = it.lineIndex ?: -1
                        val group: List<LyricNode> = lyricGroups.getOrNull(currentBottomLineIndex) ?: emptyList()
                        setLyricBottomView(group.joinToString("") { node -> node.t })
                    }
                    LyricFrameType.COOLDOWN_START -> {
                        setCooldownViewVisible(true)
                        setLyricTopView(null)
                        setLyricBottomView(null)
                        currentTopLineIndex = -1
                        currentBottomLineIndex = -1
                    }
                    LyricFrameType.COOLDOWN_END -> setCooldownViewVisible(false)
                }
            }
        }

        // Update progress for current lyrics
        updateLyricProgress(currTime)
    }

    private fun updateLyricProgress(currTime: Double) {
        // Update top lyric progress
        if (currentTopLineIndex != -1) {
            val topLine = lyricGroups.getOrNull(currentTopLineIndex) ?: emptyList()
            if (topLine.isNotEmpty()) {
                val progress = calculateLineProgress(topLine, currTime)
                setLyricTopProgress(progress)
            }
        }

        // Update bottom lyric progress
        if (currentBottomLineIndex != -1) {
            val bottomLine = lyricGroups.getOrNull(currentBottomLineIndex) ?: emptyList()
            if (bottomLine.isNotEmpty()) {
                val progress = calculateLineProgress(bottomLine, currTime)
                setLyricBottomProgress(progress)
            }
        }
    }

    private fun calculateLineProgress(line: List<LyricNode>, currTime: Double): Float {
        if (line.isEmpty()) return 0f

        val lineStartTime = line.first().s
        val lineEndTime = line.last().e

        // Before the line starts
        if (currTime < lineStartTime) return 0f
        // After the line ends
        if (currTime >= lineEndTime) return 1f

        // Find which character we're currently at
        var currentCharIndex = -1
        for (i in line.indices) {
            if (currTime >= line[i].s && currTime < line[i].e) {
                currentCharIndex = i
                break
            }
        }

        // If between characters, use the previous one
        if (currentCharIndex == -1) {
            for (i in line.indices.reversed()) {
                if (currTime >= line[i].e) {
                    currentCharIndex = i
                    break
                }
            }
            // If still not found (shouldn't happen), return full progress
            if (currentCharIndex == -1) return 1f
        }

        // Calculate progress up to current character
        var progress = currentCharIndex.toFloat() / line.size

        // Calculate progress within current character
        val char = line[currentCharIndex]
        val charDuration = char.e - char.s
        if (charDuration > 0) {
            val charElapsed = currTime - char.s
            val charProgress = (charElapsed / charDuration).toFloat().coerceIn(0f, 1f)
            progress += charProgress / line.size
        }

        return progress.coerceIn(0f, 1f)
    }

    private fun createLyricFrames(song: Song, groups: List<List<LyricNode>>): List<LyricFrame> {
        val frames = mutableListOf<LyricFrame>()

        if (groups.isEmpty()) return frames

        val firstGroup = groups[0]
        val firstGroupStartTime = firstGroup.firstOrNull()?.s ?: 0.0

        // Title frames
        if (firstGroupStartTime >= 3.0) {
            frames.add(LyricFrame(
                type = LyricFrameType.TITLE_SHOW,
                time = 0.0,
                metadata = Metadata(
                    title = song.title ?: "Unknown title",
                    artist = song.artist ?: "Unknown artist",
                    charter = song.charter ?: "Unknown charter",
                    lyricist = song.lyricist ?: "Unknown lyricist"
                )
            ))

            frames.add(LyricFrame(
                type = LyricFrameType.TITLE_HIDE,
                time = firstGroupStartTime - 3.0
            ))
        }

        val countdownableGroups = groups.filterIndexed { i, group ->
            if (group.isEmpty()) return@filterIndexed false
            val prevGroup = groups.getOrNull(i - 1)
            prevGroup == null || (prevGroup.isNotEmpty() &&
                    group.first().s - prevGroup.last().e > 8)
        }.map { it.first().s }

        countdownableGroups.forEach { startTime ->
            (3 downTo 0).forEach { count ->
                frames.add(LyricFrame(
                    type = LyricFrameType.COUNTDOWN,
                    time = startTime - count,
                    countdownFrom = count.toDouble()
                ))
            }
        }

        // Initial lyric frames
        if (groups.size >= 1 && groups[0].isNotEmpty()) {
            frames.add(LyricFrame(
                type = LyricFrameType.LYRIC_TOP,
                time = firstGroupStartTime - 3,
                lineIndex = 0
            ))
        }

        if (groups.size >= 2 && groups[1].isNotEmpty()) {
            frames.add(LyricFrame(
                type = LyricFrameType.LYRIC_BOTTOM,
                time = firstGroupStartTime - 3,
                lineIndex = 1
            ))
        }

        var flipped = false
        var i = 2
        while (i < groups.size) {
            val prevGroup = groups[i - 1]
            val currentGroup = groups[i]

            if (prevGroup.isEmpty() || currentGroup.isEmpty()) {
                i++
                continue
            }

            val prevGroupEndTime = prevGroup.last().e
            val currentGroupStartTime = currentGroup.first().s
            val timeDiff = currentGroupStartTime - prevGroupEndTime

            // Handle cooldown between groups
            if (timeDiff > 8) {
                val cooldownStartTime = prevGroupEndTime + 3
                val cooldownEndTime = currentGroupStartTime - 3

                frames.add(LyricFrame(
                    type = LyricFrameType.COOLDOWN_START,
                    time = cooldownStartTime
                ))

                frames.add(LyricFrame(
                    type = LyricFrameType.COOLDOWN_END,
                    time = cooldownEndTime
                ))

                // After cooldown, reset the flip state and add both lines
                frames.add(LyricFrame(
                    type = LyricFrameType.LYRIC_TOP,
                    time = cooldownEndTime,
                    lineIndex = i
                ))

                if (i + 1 < groups.size && groups[i + 1].isNotEmpty()) {
                    frames.add(LyricFrame(
                        type = LyricFrameType.LYRIC_BOTTOM,
                        time = cooldownEndTime,
                        lineIndex = i + 1
                    ))
                    i++
                }
                flipped = false
            } else {
                val transitionTime = prevGroup.first().s +
                        (prevGroup.last().e - prevGroup.first().s) / 3

                if (!flipped) {
                    frames.add(LyricFrame(
                        type = LyricFrameType.LYRIC_TOP,
                        time = transitionTime,
                        lineIndex = i
                    ))
                } else {
                    frames.add(LyricFrame(
                        type = LyricFrameType.LYRIC_BOTTOM,
                        time = transitionTime,
                        lineIndex = i
                    ))
                }
                flipped = !flipped
            }
            i++
        }

        return frames.sortedBy { it.time }
    }

    fun setMetadata(metadata: Metadata) {
        binding.playerLyrics.lyricTitle.text = metadata.title
        binding.playerLyrics.lyricArtist.text = metadata.artist
        binding.playerLyrics.lyricCharter.text = metadata.charter
        binding.playerLyrics.lyricLyricist.text = metadata.lyricist
    }

    fun setLyricTopView(text: String?) {
        if (text == null || text.isEmpty()) {
            binding.playerLyrics.lyricTop.visibility = View.GONE
            return
        }
        binding.playerLyrics.lyricTop.visibility = View.VISIBLE
        binding.playerLyrics.lyricTop.text = text
    }

    fun setLyricBottomView(text: String?) {
        if (text == null || text.isEmpty()) {
            binding.playerLyrics.lyricBottom.visibility = View.GONE
            return
        }
        binding.playerLyrics.lyricBottom.visibility = View.VISIBLE
        binding.playerLyrics.lyricBottom.text = text
    }

    fun setCountdownTime(time: Double) {
        if (time > 0) {
            binding.playerLyrics.lyricCountdown.visibility = View.VISIBLE
            // Display whole numbers without decimals, decimals with one place
            val displayText = if (time >= 1.0) {
                time.toInt().toString()
            } else {
                String.format("%.1f", time)
            }
            binding.playerLyrics.lyricCountdown.text = displayText
        } else {
            binding.playerLyrics.lyricCountdown.visibility = View.GONE
            binding.playerLyrics.lyricCountdown.text = "0"
        }
    }

    fun setCooldownViewVisible(visible: Boolean) {
        binding.playerLyrics.cooldownContainer.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun setLyricTopProgress(progress: Float) {
        binding.playerLyrics.lyricTop?.setKaraokeProgress(progress)
    }

    fun setLyricBottomProgress(progress: Float) {
        binding.playerLyrics.lyricBottom?.setKaraokeProgress(progress)
    }

    fun setTitleViewVisible(visible: Boolean) {
        binding.playerLyrics.lyricTitleContainer.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun clearLyricViews() {
        setLyricTopView(null)
        setLyricBottomView(null)
        setCountdownTime(0.0)
        binding.playerLyrics.lyricTitleContainer.visibility = View.GONE
        binding.playerLyrics.cooldownContainer.visibility = View.GONE
    }
}