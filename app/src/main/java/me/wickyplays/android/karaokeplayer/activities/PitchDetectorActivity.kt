package me.wickyplays.android.karaokeplayer.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import me.wickyplays.android.karaokeplayer.R
import kotlin.math.*

class PitchDetectorActivity : AppCompatActivity() {
    private lateinit var btnBack: ImageButton
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var displayLayout: View
    private lateinit var instructionsLayout: View
    private lateinit var currentNoteText: TextView
    private lateinit var lastNoteText: TextView
    private lateinit var noteText: TextView
    private lateinit var octaveText: TextView
    private lateinit var meterBar: View
    private lateinit var volumeText: TextView
    private lateinit var errorMessage: TextView

    private var audioRecord: AudioRecord? = null
    private var isListening = false
    private var bufferSize = 0
    private val sampleRate = 44100
    private val noteStrings = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private var currentNote: NoteData? = null
    private var lastNote: NoteData? = null
    private var volumePercent = 0
    private val handler = Handler(Looper.getMainLooper())
    private val audioPermissionCode = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pitch_detector)

        initViews()
        setupListeners()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        displayLayout = findViewById(R.id.display)
        instructionsLayout = findViewById(R.id.instructions)
        currentNoteText = findViewById(R.id.currentNoteText)
        lastNoteText = findViewById(R.id.lastNoteText)
        noteText = findViewById(R.id.noteText)
        octaveText = findViewById(R.id.octaveText)
        meterBar = findViewById(R.id.meterBar)
        volumeText = findViewById(R.id.volumeText)
        errorMessage = findViewById(R.id.errorMessage)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
            finish()
        }

        btnStart.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    audioPermissionCode
                )
            } else {
                startDetection()
            }
        }

        btnStop.setOnClickListener { stopDetection() }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == audioPermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startDetection()
            } else {
                showError(getString(R.string.pitch_detector_error_microphone))
            }
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startDetection() {
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
            updateUIState()

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
            showError(e.message ?: getString(R.string.pitch_detector_error_microphone))
            stopDetection()
        }
    }

    private fun processAudioBuffer(buffer: ShortArray, length: Int) {
        // Calculate volume
        var sum = 0.0
        for (i in 0 until length) {
            sum += abs(buffer[i].toDouble())
        }
        volumePercent = min(100, (sum / length / 32768 * 200).toInt())

        // Convert to float for pitch detection
        val floatBuffer = FloatArray(length)
        for (i in 0 until length) {
            floatBuffer[i] = buffer[i].toFloat() / 32768.0f
        }

        val pitch = autoCorrelate(floatBuffer, sampleRate.toFloat())

        if (pitch == -1f) {
            currentNote = null
        } else {
            val pitchRounded = pitch.roundToInt()
            val noteNumber = noteFromPitch(pitch)
            val noteText = noteStrings[noteNumber % 12]
            val detuneAmount = centsOffFromPitch(pitch, noteNumber)

            currentNote = NoteData(
                noteText = noteText,
                pitchRounded = pitchRounded,
                noteNumber = noteNumber,
                detuneAmount = detuneAmount
            )
            lastNote = currentNote
        }

        handler.post {
            updateDisplay()
        }
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
        return ((1200 * ln(frequency / frequencyFromNoteNumber(note)) / ln(2f).toInt())).roundToInt()
    }

    private fun updateDisplay() {
        currentNote?.let { note ->
            val detuneText = if (note.detuneAmount != 0) {
                val detuneRes = if (note.detuneAmount > 0)
                    R.string.pitch_detector_detune_sharp else R.string.pitch_detector_detune_flat
                " ${abs(note.detuneAmount)} ${getString(detuneRes)}"
            } else ""

            currentNoteText.text = getString(
                R.string.pitch_detector_current_note,
                note.noteText,
                note.pitchRounded
            ) + detuneText

            currentNoteText.setTextColor(
                ContextCompat.getColor(this,
                    if (note.detuneAmount > 0) R.color.blue else
                        if (note.detuneAmount < 0) R.color.red else android.R.color.black)
            )

            noteText.text = note.noteText
            octaveText.text = getString(R.string.pitch_detector_octave, (note.noteNumber / 12).toString())
        } ?: run {
            currentNoteText.text = getString(R.string.pitch_detector_no_note)
            currentNoteText.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            noteText.text = getString(R.string.pitch_detector_na)
            octaveText.text = getString(R.string.pitch_detector_octave, getString(R.string.pitch_detector_na))
        }

        lastNote?.let { note ->
            lastNoteText.text = getString(
                R.string.pitch_detector_last_note,
                note.noteText,
                note.pitchRounded
            )
        } ?: run {
            lastNoteText.text = ""
        }

        meterBar.layoutParams.width = (volumePercent * resources.displayMetrics.density).toInt()
        meterBar.requestLayout()
        volumeText.text = getString(R.string.pitch_detector_volume, volumePercent)
    }

    private fun updateUIState() {
        btnStart.isEnabled = !isListening
        btnStop.isEnabled = isListening
        displayLayout.visibility = if (isListening || lastNote != null) View.VISIBLE else View.GONE
        instructionsLayout.visibility = if (isListening || lastNote != null) View.GONE else View.VISIBLE
    }

    private fun showError(message: String) {
        errorMessage.text = message
        errorMessage.visibility = View.VISIBLE
    }

    private fun hideError() {
        errorMessage.visibility = View.GONE
    }

    private fun stopDetection() {
        isListening = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // Ignore
        }
        audioRecord = null
        updateUIState()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDetection()
    }

    data class NoteData(
        val noteText: String,
        val pitchRounded: Int,
        val noteNumber: Int,
        val detuneAmount: Int
    )
}