package me.wickyplays.android.karaokeplayer.pitchdetector

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

class PitchDetectorCore(
    private val sampleRate: Int = 44100,
    private val onPitchDetected: (NoteData?) -> Unit,
    private val onVolumeUpdate: (Int) -> Unit,
    private val onError: (String) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    var isListening = false
    private var bufferSize = 0
    private val noteStrings = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    data class NoteData(
        val noteText: String,
        val pitchRounded: Int,
        val noteNumber: Int,
        val detuneAmount: Int
    )

    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    fun startDetection() {
        if (isListening) return

        bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            bufferSize = sampleRate * 2
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            audioRecord?.startRecording()
            isListening = true

            Thread {
                val buffer = ShortArray(bufferSize)
                while (isListening) {
                    val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                    if (read > 0) {
                        processAudioBuffer(buffer, read)
                    }
                }
            }.start()
        } catch (e: Exception) {
            onError(e.message ?: "Microphone access error")
            stopDetection()
        }
    }

    fun stopDetection() {
        isListening = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // Ignore
        }
        audioRecord = null
    }

    private fun processAudioBuffer(buffer: ShortArray, length: Int) {
        // Calculate volume
        var sum = 0.0
        for (i in 0 until length) {
            sum += abs(buffer[i].toDouble())
        }
        val volumePercent = minOf(100, (sum / length / 32768 * 200).toInt())
        onVolumeUpdate(volumePercent)

        // Convert to float for pitch detection
        val floatBuffer = FloatArray(length) { i -> buffer[i].toFloat() / 32768.0f }
        val pitch = autoCorrelate(floatBuffer, sampleRate.toFloat())

        val noteData = if (pitch == -1f) {
            null
        } else {
            val pitchRounded = pitch.roundToInt()
            val noteNumber = noteFromPitch(pitch)
            val noteText = noteStrings[noteNumber % 12]
            val detuneAmount = centsOffFromPitch(pitch, noteNumber)

            NoteData(
                noteText = noteText,
                pitchRounded = pitchRounded,
                noteNumber = noteNumber,
                detuneAmount = detuneAmount
            )
        }

        onPitchDetected(noteData)
    }

    private fun autoCorrelate(buf: FloatArray, sampleRate: Float): Float {
        val bufferSize = buf.size
        var rms = 0.0f

        for (i in 0 until bufferSize) {
            val value = buf[i]
            rms += value * value
        }
        rms = sqrt(rms / bufferSize)
        if (rms < 0.01f) return -1f

        var r1 = 0
        var r2 = bufferSize - 1
        val thres = 0.2f
        for (i in 0 until bufferSize / 2) {
            if (abs(buf[i]) < thres) {
                r1 = i
                break
            }
        }
        for (i in 1 until bufferSize / 2) {
            if (abs(buf[bufferSize - i]) < thres) {
                r2 = bufferSize - i
                break
            }
        }

        val newBuf = buf.copyOfRange(r1, r2)
        val newBufferSize = newBuf.size

        val c = FloatArray(newBufferSize) { 0f }
        for (i in 0 until newBufferSize) {
            for (j in 0 until newBufferSize - i) {
                c[i] += newBuf[j] * newBuf[j + i]
            }
        }

        var d = 0
        while (d < newBufferSize - 1 && c[d] > c[d + 1]) {
            d++
        }

        var maxval = -1f
        var maxpos = -1
        for (i in d until newBufferSize) {
            if (c[i] > maxval) {
                maxval = c[i]
                maxpos = i
            }
        }
        var t0 = maxpos.toFloat()

        val x1 = c[maxpos - 1]
        val x2 = c[maxpos]
        val x3 = c[maxpos + 1]
        val a = (x1 + x3 - 2 * x2) / 2
        val b = (x3 - x1) / 2
        if (a != 0f) t0 -= b / (2 * a)

        return sampleRate / t0
    }

    private fun noteFromPitch(frequency: Float): Int {
        val noteNum = 12 * (ln(frequency / 440f) / ln(2f))
        return (noteNum + 69).roundToInt()
    }

    private fun frequencyFromNoteNumber(note: Int): Float {
        return 440f * 2f.pow((note - 69) / 12f)
    }

    private fun centsOffFromPitch(frequency: Float, note: Int): Int {
        return (1200 * ln(frequency / frequencyFromNoteNumber(note)) / ln(2f)).roundToInt()
    }
}