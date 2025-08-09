package me.wickyplays.android.karaokeplayer.activities

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.view.children
import me.wickyplays.android.karaokeplayer.R
import me.wickyplays.android.karaokeplayer.databinding.ActivityHomeBinding
import me.wickyplays.android.karaokeplayer.player.AudioManager

class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding
    private var selectedIndex = 0

    private var buttonActions = emptyList<ButtonAction>()
    private val selectedColor = "#4A4A4A".toColorInt()
    private val unselectedColor = Color.BLACK
    private val textColor = Color.WHITE
    private var frameCount = 0
    private var lastUpdateTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val fpsUpdateInterval = 1000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buttonActions = listOf(
            ButtonAction(getString(R.string.home_play_karaoke), {
                val intent = Intent(this, PlayerActivity::class.java)
                startActivity(intent)
                finish()
            }),
            ButtonAction(getString(R.string.home_edit_karaoke), {}),
            ButtonAction(getString(R.string.home_pitch_detection), {
                val intent = Intent(this, PitchDetectorActivity::class.java)
                startActivity(intent)
                finish()
            }),
            ButtonAction(getString(R.string.home_settings), {}),
            ButtonAction(getString(R.string.home_exit)) { finishAffinity() }
        )

        binding = ActivityHomeBinding.inflate(layoutInflater)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContentView(binding.root)
        setupBackgroundVideo()
        playTitleMusic()
        setupButtons()
        binding.buttonContainer.children.firstOrNull()?.let {
            it.requestFocus()
            updateButtonSelection()
        }
        startFpsCounter()
    }

    private fun setupBackgroundVideo() {
        binding.videoView.setMediaController(null)
        binding.videoView.setVideoURI("android.resource://${packageName}/${R.raw.bg}".toUri())
        binding.videoView.setOnPreparedListener {
            it.isLooping = true
            binding.videoView.start()
        }
        binding.videoView.setOnErrorListener { mp, what, extra ->
            Log.e("VideoView", "Error occurred: what=$what, extra=$extra")
            true
        }
    }

    private fun setupButtons() {
        binding.buttonContainer.removeAllViews()
        buttonActions.forEachIndexed { index, buttonAction ->
            val button = Button(this).apply {
                text = buttonAction.label
                setTextColor(textColor)
                backgroundTintList = ColorStateList.valueOf(unselectedColor)
                textSize = 20f
                isAllCaps = false
                setOnClickListener {
                    selectedIndex = index
                    updateButtonSelection()
                    buttonAction.action.invoke()
                }
                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        selectedIndex = index
                        updateButtonSelection()
                    }
                }
            }
            binding.buttonContainer.addView(button)
        }
        binding.root.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                handleKeyEvent(keyCode)
            } else {
                false
            }
        }
    }

    private fun handleKeyEvent(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                selectPreviousButton()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                selectNextButton()
                true
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                buttonActions[selectedIndex].action.invoke()
                true
            }
            else -> false
        }
    }

    private fun selectPreviousButton() {
        if (selectedIndex > 0) {
            selectedIndex--
            binding.buttonContainer.getChildAt(selectedIndex).requestFocus()
            updateButtonSelection()
            AudioManager.getInstance(this).playSoundEffect(R.raw.click)
        }
    }

    private fun selectNextButton() {
        if (selectedIndex < buttonActions.size - 1) {
            selectedIndex++
            binding.buttonContainer.getChildAt(selectedIndex).requestFocus()
            updateButtonSelection()
            AudioManager.getInstance(this).playSoundEffect(R.raw.click)
        }
    }

    private fun updateButtonSelection() {
        binding.buttonContainer.children.forEachIndexed { index, view ->
            val button = view as Button
            button.backgroundTintList = ColorStateList.valueOf(
                if (index == selectedIndex) selectedColor else unselectedColor
            )
        }
    }

    private fun playTitleMusic() {
        AudioManager.getInstance(this).playHomeMusic()
    }

    private fun startFpsCounter() {
        val fpsView = findViewById<TextView>(R.id.fps_view)
        val updateFps = object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                frameCount++
                if (currentTime - lastUpdateTime >= fpsUpdateInterval) {
                    val fps = (frameCount * 1000.0 / (currentTime - lastUpdateTime)).toInt()
                    fpsView.text = "FPS: $fps"
                    frameCount = 0
                    lastUpdateTime = currentTime
                }
                binding.videoView.invalidate()
                handler.post(this)
            }
        }
        lastUpdateTime = System.currentTimeMillis()
        handler.post(updateFps)
    }

    override fun onPause() {
        super.onPause()
        binding.videoView.pause()
        AudioManager.getInstance(this).stopHomeMusic()
    }

    override fun onResume() {
        super.onResume()
        binding.videoView.start()
        playTitleMusic()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.videoView.stopPlayback()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
                WindowInsets.Type.displayCutout()
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                or View.SYSTEM_UI_FLAG_FULLSCREEN
                                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        )
            }
        }
    }

    data class ButtonAction(val label: String, val action: () -> Unit)
}