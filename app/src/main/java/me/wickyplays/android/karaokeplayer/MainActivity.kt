package me.wickyplays.android.karaokeplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import me.wickyplays.android.karaokeplayer.activities.HomeActivity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    companion object {
        init {
            System.loadLibrary("native_lib")
        }

        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

    // New permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            initializeSynthAndStartHome()
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Check and request permissions
        if (hasAllPermissions()) {
            initializeSynthAndStartHome()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    private fun hasAllPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun initializeSynthAndStartHome() {
        try {
            // Use app-specific storage for the soundfont
            val soundfontFile = File(filesDir, "general_user.sf2")
            if (!soundfontFile.exists()) {
                copyAssetToFile("general_user.sf2", soundfontFile)
            }
            fluidsynthHelloWorld(soundfontFile.absolutePath)
            startHomeActivity()
        } catch (e: IOException) {
            e.printStackTrace()
            finish()
        }
    }

    private fun startHomeActivity() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }

    @Throws(IOException::class)
    private fun copyAssetToFile(assetName: String, destinationFile: File) {
        assets.open(assetName).use { input ->
            FileOutputStream(destinationFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    external fun fluidsynthHelloWorld(soundfontPath: String)
}