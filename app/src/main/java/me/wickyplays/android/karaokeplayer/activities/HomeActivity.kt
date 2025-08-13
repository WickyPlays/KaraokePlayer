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
import androidx.core.view.isVisible
import me.wickyplays.android.karaokeplayer.R
import me.wickyplays.android.karaokeplayer.cores.player.AudioManager
import me.wickyplays.android.karaokeplayer.databinding.ActivityHomeBinding

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
        setupButtonActions()

        binding = ActivityHomeBinding.inflate(layoutInflater)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContentView(binding.root)

        setupBackgroundVideo()
        setupButtons()
        setupInitialFocus()
        setupSystemUi()

        playTitleMusic()
        startFpsCounter()
    }

    private fun setupButtonActions() {
        buttonActions = listOf(
            ButtonAction(getString(R.string.home_play_karaoke), {
                startActivity(Intent(this, PlayerActivity::class.java))
                finish()
            }),
            ButtonAction(getString(R.string.home_open_directory), {
                startActivity(Intent(this, DirectoriesActivity::class.java))
            }),
            ButtonAction(getString(R.string.home_edit_karaoke), ::showEditNotificationDialog),
            ButtonAction(getString(R.string.home_pitch_detection), {
                startActivity(Intent(this, PitchDetectorActivity::class.java))
                playClickSound()
            }),
            ButtonAction(getString(R.string.home_settings), {
                startActivity(Intent(this, SettingsActivity::class.java))
                playClickSound()
            }),
            ButtonAction(getString(R.string.home_exit), ::exitApp)
        )
    }

    private fun setupBackgroundVideo() {
        binding.videoView.apply {
            setMediaController(null)
            setVideoURI("android.resource://${packageName}/${R.raw.bg}".toUri())
            setOnPreparedListener {
                it.isLooping = true
                start()
            }
            setOnErrorListener { _, what, extra ->
                Log.e("VideoView", "Error occurred: what=$what, extra=$extra")
                true
            }
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
                    if (binding.homeEditNotification.root.isVisible) {
                        binding.homeEditNotification.root.visibility = View.GONE
                    } else {
                        buttonAction.action.invoke()
                    }
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
            if (event.action == KeyEvent.ACTION_DOWN) handleKeyEvent(keyCode) else false
        }
    }

    private fun setupInitialFocus() {
        binding.buttonContainer.children.firstOrNull()?.requestFocus()
        updateButtonSelection()
    }

    private fun setupSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
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
                if (binding.homeEditNotification.root.isVisible) {
                    binding.homeEditNotification.root.visibility = View.GONE
                } else {
                    buttonActions[selectedIndex].action.invoke()
                }
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
        }
    }

    private fun selectNextButton() {
        if (selectedIndex < buttonActions.size - 1) {
            selectedIndex++
            binding.buttonContainer.getChildAt(selectedIndex).requestFocus()
            updateButtonSelection()
        }
    }

    private fun updateButtonSelection() {
        playClickSound()
        binding.buttonContainer.children.forEachIndexed { index, view ->
            (view as Button).backgroundTintList = ColorStateList.valueOf(
                if (index == selectedIndex) selectedColor else unselectedColor
            )
        }
    }

    private fun playTitleMusic() {
        AudioManager.getInstance(this).playHomeMusic()
    }

    private fun playClickSound() {
        AudioManager.getInstance(this).playSoundEffect(R.raw.click)
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

    private fun showEditNotificationDialog() {
        binding.homeEditNotification.apply {
            root.visibility = View.VISIBLE
            closeButton.setOnClickListener { root.visibility = View.GONE }
            gotItButton.setOnClickListener { root.visibility = View.GONE }
        }
        playClickSound()
    }

    private fun exitApp() {
        playClickSound()
        finishAffinity()
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
        if (hasFocus) setupSystemUi()
    }

    private data class ButtonAction(val label: String, val action: () -> Unit)
}