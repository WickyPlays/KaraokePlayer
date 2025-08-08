package me.wickyplays.android.karaokeplayer.activities

import android.Manifest
import android.content.pm.PackageManager
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
import me.wickyplays.android.karaokeplayer.pitchdetector.PitchDetectorCore
import kotlin.math.abs

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

    private var pitchDetectorCore: PitchDetectorCore? = null
    private var currentNote: PitchDetectorCore.NoteData? = null
    private var lastNote: PitchDetectorCore.NoteData? = null
    private var volumePercent = 0
    private val handler = Handler(Looper.getMainLooper())
    private val audioPermissionCode = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pitch_detector)

        initViews()
        setupListeners()
        initializePitchDetector()
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
        btnBack.setOnClickListener { onBackPressed() }

        btnStart.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    audioPermissionCode
                )
            } else {
                pitchDetectorCore?.startDetection()
            }
        }

        btnStop.setOnClickListener {
            pitchDetectorCore?.stopDetection()
            updateUIState()
        }
    }

    private fun initializePitchDetector() {
        pitchDetectorCore = PitchDetectorCore(
            onPitchDetected = { noteData ->
                currentNote = noteData
                lastNote = noteData ?: lastNote
                handler.post { updateDisplay() }
            },
            onVolumeUpdate = { volume ->
                volumePercent = volume
                handler.post { updateDisplay() }
            },
            onError = { error ->
                handler.post { showError(error) }
            }
        )
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
                pitchDetectorCore?.startDetection()
            } else {
                showError(getString(R.string.pitch_detector_error_microphone))
            }
        }
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
                ContextCompat.getColor(
                    this,
                    if (note.detuneAmount > 0) R.color.blue else
                        if (note.detuneAmount < 0) R.color.red else android.R.color.black
                )
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
        btnStart.isEnabled = pitchDetectorCore?.isListening != true
        btnStop.isEnabled = pitchDetectorCore?.isListening == true
        displayLayout.visibility = if (pitchDetectorCore?.isListening == true || lastNote != null) View.VISIBLE else View.GONE
        instructionsLayout.visibility = if (pitchDetectorCore?.isListening == true || lastNote != null) View.GONE else View.VISIBLE
    }

    private fun showError(message: String) {
        errorMessage.text = message
        errorMessage.visibility = View.VISIBLE
    }

    private fun hideError() {
        errorMessage.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        pitchDetectorCore?.stopDetection()
    }
}