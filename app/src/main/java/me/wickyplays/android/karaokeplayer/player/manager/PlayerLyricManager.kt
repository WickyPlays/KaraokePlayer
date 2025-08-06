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
        }
    }

    fun updateLyrics(currTime: Double) {
        if (frames.isEmpty()) return

        val passedFrames = frames.filter { it.time <= currTime }
        passedFrames.forEach {
            if (it.available) {
                it.available = false
                when (it.type) {
                    LyricFrameType.TITLE_SHOW -> setTitleViewVisible(true)
                    LyricFrameType.TITLE_HIDE -> setTitleViewVisible(false)
                    LyricFrameType.COUNTDOWN -> setCountdownTime(it.countdownFrom ?: 0.0)
                    LyricFrameType.LYRIC_TOP -> {
                        val group: List<LyricNode> = lyricGroups.getOrNull(it.lineIndex ?: 0) ?: emptyList()
                        setLyricTopView(group.joinToString("") { node -> node.t })
                    }
                    LyricFrameType.LYRIC_BOTTOM -> {
                        val group: List<LyricNode> = lyricGroups.getOrNull(it.lineIndex ?: 0) ?: emptyList()
                        setLyricBottomView(group.joinToString("") { node -> node.t })
                    }
                    LyricFrameType.COOLDOWN_START -> {
                        setCooldownViewVisible(true)
                        setLyricTopView(null)
                        setLyricBottomView(null)
                    }
                    LyricFrameType.COOLDOWN_END -> setCooldownViewVisible(false)
                }
            }
        }

        // Update progress for active lyric lines
        updateLyricProgress(currTime)
    }

    private fun updateLyricProgress(currTime: Double) {
        lyricGroups.forEachIndexed { groupIndex, group ->
            if (group.isNotEmpty()) {
                val firstNode = group.first()
                val lastNode = group.last()

                // Check if current time is within this group's duration
                if (currTime >= firstNode.s && currTime <= lastNode.e) {
                    // Calculate total duration of the group
                    val groupDuration = lastNode.e - firstNode.s
                    if (groupDuration > 0) {
                        // Find the current active node
                        val activeNode = group.find { currTime >= it.s && currTime <= it.e }
                        if (activeNode != null) {
                            // Calculate progress within the current node
                            val nodeProgress = ((currTime - activeNode.s) / (activeNode.e - activeNode.s)).toFloat()
                            // Calculate character-based progress
                            val totalChars = group.joinToString("").length
                            val charsBefore = lyricGroups.subList(0, groupIndex).sumOf { g -> g.joinToString("").length }
                            val currentGroupText = group.joinToString("")
                            var charsUpToNode = 0
                            val nodeIndex = group.indexOf(activeNode)
                            for (i in 0 until nodeIndex) {
                                charsUpToNode += group[i].t.length
                            }
                            val progressInGroup = (charsUpToNode + (nodeProgress * activeNode.t.length)) / totalChars

                            // Apply progress to appropriate view based on frame type
                            val activeFrame = frames.find {
                                it.lineIndex == groupIndex &&
                                        (it.type == LyricFrameType.LYRIC_TOP || it.type == LyricFrameType.LYRIC_BOTTOM) &&
                                        currTime >= it.time
                            }
                            if (activeFrame != null) {
                                val progress = progressInGroup.coerceIn(0f, 1f)
                                if (activeFrame.type == LyricFrameType.LYRIC_TOP) {
                                    setLyricTopProgress(progress)
                                } else {
                                    setLyricBottomProgress(progress)
                                }
                            }
                        }
                    }
                }
            }
        }
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

        // Countdown frames
        val countdownableGroups = groups.filterIndexed { i, group ->
            if (group.isEmpty()) return@filterIndexed false
            val prevGroup = groups.getOrNull(i - 1)
            prevGroup == null || (prevGroup.isNotEmpty() &&
                    group.first().s - prevGroup.last().e > 8)
        }.map { it.first().s }

        countdownableGroups.forEach { startTime ->
            // Whole number countdown (3, 2, 1)
            (3 downTo 1).forEach { count ->
                frames.add(LyricFrame(
                    type = LyricFrameType.COUNTDOWN,
                    time = startTime - count,
                    countdownFrom = count.toDouble()
                ))
            }
            // Decimal countdown (0.9, 0.8, ..., 0.0)
            (9 downTo 0).forEach { tenth ->
                frames.add(LyricFrame(
                    type = LyricFrameType.COUNTDOWN,
                    time = startTime - (tenth / 10.0),
                    countdownFrom = tenth / 10.0
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
        for (i in 2 until groups.size) {
            val prevGroup = groups[i - 1]
            val currentGroup = groups[i]

            if (prevGroup.isEmpty() || currentGroup.isEmpty()) {
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
                }
                flipped = false
            } else {
                val transitionTime = prevGroup.first().s +
                        (prevGroup.last().e - prevGroup.first().s) / 2

                frames.add(LyricFrame(
                    type = if (!flipped) LyricFrameType.LYRIC_TOP else LyricFrameType.LYRIC_BOTTOM,
                    time = transitionTime,
                    lineIndex = i
                ))
                flipped = !flipped
            }
        }

        Log.d("PlayerLyricManager", "In createLyricFrames: ${frames.size} frames")

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
        binding.playerLyrics.lyricTop.text = ""
        binding.playerLyrics.lyricBottom.text = ""
    }
}