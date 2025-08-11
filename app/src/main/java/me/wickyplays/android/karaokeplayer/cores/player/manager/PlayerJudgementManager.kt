package me.wickyplays.android.karaokeplayer.cores.player.manager

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import me.wickyplays.android.karaokeplayer.databinding.ActivityPlayerBinding
import me.wickyplays.android.karaokeplayer.cores.player.obj.JudgementNode
import me.wickyplays.android.karaokeplayer.cores.player.obj.Song
import kotlin.math.*
import java.io.File

class PlayerJudgementManager(val binding: ActivityPlayerBinding) {

    private val noteStrings =
        arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val MIC_DELAY_COMPENSATION = 1.0 // 1 second delay compensation
    private val minNote = 40
    private val maxNote = 100
    private val BUFFER_SIZE = 2048
    private val SAMPLE_RATE = 44100

    var score = 0
    private var judgementNodes: List<JudgementNode> = emptyList()
    private var audioRecord: AudioRecord? = null
    private var isDetecting = false
    private var detectionThread: Thread? = null
    private var currentDetectedNote: Int? = null
    private var currentBaseNote: Int? = null
    private var volumePercent = 0f
    private var ignoreOctaves = true

    fun initJudgementFromPath(song: Song): List<JudgementNode> {
        if (song.judgementPath.isNullOrBlank()) {
            judgementNodes = emptyList()
            return emptyList()
        }

        val newJudgementNodes = try {
            val judgementPath = song.judgementPath
            if (judgementPath != null) {
                Log.d("Player", "Reading judgement file: $judgementPath")
                val judgementFile = File(judgementPath)
                if (judgementFile.exists()) {
                    val judgementContent = judgementFile.readText()
                    parseJudgementJson(judgementContent)
                } else {
                    Log.e("Player", "Judgement file not found at path: $judgementPath")
                    emptyList()
                }
            } else {
                Log.d("Player", "No judgement path provided for song")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("Player", "Error reading/parsing judgement file: ${e.message}")
            emptyList()
        }

        judgementNodes = newJudgementNodes
        return newJudgementNodes
    }

    private fun parseJudgementJson(jsonContent: String): List<JudgementNode> {
        return try {
            val jsonArray = org.json.JSONArray(jsonContent)
            val nodes = mutableListOf<JudgementNode>()

            for (i in 0 until jsonArray.length()) {
                var nodeObj = jsonArray.getJSONObject(i)
                nodes.add(
                    JudgementNode(
                        n = nodeObj.getInt("n"),
                        s = nodeObj.getDouble("s"),
                        e = nodeObj.getDouble("e"),
                        hit = false
                    )
                )
            }
            nodes
        } catch (e: Exception) {
            Log.e("Player", "Error parsing judgement JSON: ${e.message}")
            emptyList()
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startPitchDetection(context: Context) {
        if (isDetecting) return

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            )

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
                max(BUFFER_SIZE * 2, minBufferSize)
            )

            audioRecord?.startRecording()
            isDetecting = true

            detectionThread = Thread {
                val buffer = FloatArray(BUFFER_SIZE)

                while (isDetecting) {
                    val read =
                        audioRecord?.read(buffer, 0, BUFFER_SIZE, AudioRecord.READ_BLOCKING) ?: 0
                    if (read > 0) {
                        val pitch = autoCorrelate(buffer, SAMPLE_RATE.toDouble())
                        processAudioData(pitch, buffer)
                    }
                }
            }

            detectionThread?.start()
        } catch (e: Exception) {
            Log.e("Player", "Error starting pitch detection: ${e.message}")
            stopPitchDetection()
        }
    }

    fun stopPitchDetection() {
        isDetecting = false
        detectionThread?.interrupt()
        detectionThread = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e("Player", "Error stopping audio recorder: ${e.message}")
        }
        audioRecord = null

        currentDetectedNote = null
        currentBaseNote = null
        volumePercent = 0f
    }

    private fun processAudioData(pitch: Double, buffer: FloatArray) {
        // Calculate volume
        var sum = 0f
        for (i in buffer.indices) {
            sum += abs(buffer[i])
        }
        volumePercent = min(100f, (sum / buffer.size) * 200f)

        if (pitch == -1.0) {
            currentDetectedNote = null
            currentBaseNote = null
        } else {
            val noteNumber = noteFromPitch(pitch)
            currentDetectedNote = noteNumber
            currentBaseNote = noteNumber % 12
        }
    }

    fun updateJudgement(currentTime: Double) {
        updateHitCount()

        if (currentDetectedNote == null) return

        Log.d("Player", "Current note: $currentDetectedNote")

        val startCheckTime = max(0.0, currentTime - MIC_DELAY_COMPENSATION)
        val endCheckTime = currentTime

        val notesToCheck = if (ignoreOctaves) {
            getAllOctaveNotes(currentDetectedNote!!)
        } else {
            listOf(currentDetectedNote!!)
        }

        judgementNodes = judgementNodes.map { node ->
            if (notesToCheck.contains(node.n) &&
                ((node.s >= startCheckTime && node.s <= endCheckTime) ||
                        (node.e >= startCheckTime && node.e <= endCheckTime) ||
                        (node.s <= startCheckTime && node.e >= endCheckTime)) &&
                !node.hit
            ) {
                node.copy(hit = true)
            } else {
                node
            }
        }
    }

    private fun getAllOctaveNotes(noteNumber: Int): List<Int> {
        val baseNote = noteNumber % 12
        val notes = mutableListOf<Int>()
        var n = baseNote
        while (n <= maxNote) {
            if (n >= minNote) {
                notes.add(n)
            }
            n += 12
        }
        return notes
    }

    fun updateHitCount() {
        val hitCount = judgementNodes.count { it.hit }
        val total = judgementNodes.size
        score = if (total > 0) (hitCount * 100 / total) else 0
        binding.playerQueueBar.hitText.text = "Hit: $hitCount/$total"
    }

    fun resetScore() {
        score = 0
        judgementNodes = judgementNodes.map { it.copy(hit = false) }
        updateHitCount()
    }

    fun getJudgementNodes(): List<JudgementNode> {
        return judgementNodes
    }

    fun setIgnoreOctaves(ignore: Boolean) {
        ignoreOctaves = ignore
    }

    // Audio processing functions
    private fun autoCorrelate(buf: FloatArray, sampleRate: Double): Double {
        try {
            var rms = 0.0
            for (i in buf.indices) {
                val `val` = buf[i].toDouble()
                rms += `val` * `val`
            }
            rms = sqrt(rms / buf.size)
            if (rms < 0.01) return -1.0

            var r1 = 0
            var r2 = buf.size - 1
            val thres = 0.2
            for (i in 0 until buf.size / 2) {
                if (abs(buf[i]) < thres) {
                    r1 = i
                    break
                }
            }
            for (i in 1 until buf.size / 2) {
                if (abs(buf[buf.size - i]) < thres) {
                    r2 = buf.size - i
                    break
                }
            }

            val newBuf = buf.copyOfRange(r1, r2)
            val c = DoubleArray(newBuf.size) { 0.0 }

            for (i in c.indices) {
                for (j in 0 until newBuf.size - i) {
                    c[i] += newBuf[j] * newBuf[j + i]
                }
            }

            var d = 0
            while (d < c.size - 1 && c[d] > c[d + 1]) {
                d++
            }

            var maxval = -1.0
            var maxpos = -1
            for (i in d until c.size) {
                if (c[i] > maxval) {
                    maxval = c[i]
                    maxpos = i
                }
            }
            var T0 = maxpos.toDouble()

            val x1 = c[maxpos - 1]
            val x2 = c[maxpos]
            val x3 = c[maxpos + 1]
            val a = (x1 + x3 - 2 * x2) / 2
            val b = (x3 - x1) / 2
            if (a != 0.0) T0 -= b / (2 * a)

            return sampleRate / T0
        } catch (e: Exception) {
            Log.e("Player", "Error calculating auto correlation: ${e.message}")
            return -1.0
        }
    }

    private fun noteFromPitch(frequency: Double): Int {
        val noteNum = 12 * (ln(frequency / 440.0) / ln(2.0))
        return (noteNum + 69).roundToInt()
    }

    private fun frequencyFromNoteNumber(note: Int): Double {
        return 440.0 * 2.0.pow((note - 69) / 12.0)
    }
}