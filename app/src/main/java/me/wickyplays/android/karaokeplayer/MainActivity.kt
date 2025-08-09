package me.wickyplays.android.karaokeplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import me.wickyplays.android.karaokeplayer.activities.HomeActivity
import java.io.IOException

class MainActivity : AppCompatActivity() {

    companion object {
        init {
            System.loadLibrary("native_lib")
        }

        private const val PERMISSION_REQUEST_CODE = 100
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_main)
        checkPermissions()
    }

    private fun checkPermissions() {
        try {
            val tempSoundfontPath: String? = copyAssetToTempFile("general_user.sf2")
            fluidsynthHelloWorld(tempSoundfontPath!!)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            startHomeActivity()
        } else {
            requestPermissions(missingPermissions.toTypedArray())
        }
    }

    private fun requestPermissions(permissions: Array<String>) {
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
    }

    private fun startHomeActivity() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                if (allGranted) {
                    startHomeActivity()
                } else {
                    finish()
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun copyAssetToTempFile(fileName: String): String {
        getAssets().open(fileName).use { `is` ->
            val tempFileName = "tmp_" + fileName
            openFileOutput(tempFileName, MODE_PRIVATE).use { fos ->
                var bytes_read: Int
                val buffer = ByteArray(4096)
                while ((`is`.read(buffer).also { bytes_read = it }) != -1) {
                    fos.write(buffer, 0, bytes_read)
                }
            }
            return getFilesDir().toString() + "/" + tempFileName
        }
    }

    external fun fluidsynthHelloWorld(soundfontPath: String)
}