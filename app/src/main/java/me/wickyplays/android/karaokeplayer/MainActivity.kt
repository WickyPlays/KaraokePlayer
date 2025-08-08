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

        private const val STORAGE_PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_main)
        checkStoragePermission()
    }

    private fun checkStoragePermission() {
        try {
            val tempSoundfontPath: String? = copyAssetToTempFile("general_user.sf2")
            fluidsynthHelloWorld(tempSoundfontPath!!)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED -> {
                startHomeActivity()
            }
            else -> {
                requestStoragePermission()
            }
        }
    }

    private fun requestStoragePermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), STORAGE_PERMISSION_REQUEST_CODE)
    }

    private fun startHomeActivity() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            STORAGE_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
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
