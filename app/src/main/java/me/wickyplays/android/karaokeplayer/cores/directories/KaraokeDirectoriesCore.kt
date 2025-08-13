package me.wickyplays.android.karaokeplayer.cores.directories

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat

class KaraokeDirectoriesCore private constructor() {

    private var appContext: Context? = null

    companion object {
        @Volatile
        private var instance: KaraokeDirectoriesCore? = null

        fun getInstance(): KaraokeDirectoriesCore {
            return instance ?: synchronized(this) {
                instance ?: KaraokeDirectoriesCore().also { instance = it }
            }
        }
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun getAllDirectories(): List<String> {
        return try {
            val filesDir = appContext!!.filesDir
            if (filesDir.exists() && filesDir.isDirectory) {
                filesDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    fun getAllExternalDirectories(): List<String> {
        return try {
            val externalFilesDir = appContext!!.getExternalFilesDir(null)
            if (externalFilesDir != null && externalFilesDir.exists() && externalFilesDir.isDirectory) {
                externalFilesDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    fun getItemsFromGroup(group: String): Map<String, List<String>> {
        return try {
            val externalFilesDir = appContext!!.getExternalFilesDir(null)
            if (externalFilesDir != null && externalFilesDir.exists() && externalFilesDir.isDirectory) {
                val groupDir = File(externalFilesDir, group)
                if (groupDir.exists() && groupDir.isDirectory) {
                    val subDirs = groupDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()

                    val resultMap = mutableMapOf<String, List<String>>()

                    listOf("bg", "songs", "soundfonts", "se").forEach { category ->
                        val categoryDir = File(groupDir, category)
                        if (categoryDir.exists() && categoryDir.isDirectory) {
                            val items = categoryDir.listFiles()?.map { it.name } ?: emptyList()
                            resultMap[category] = items
                        } else {
                            resultMap[category] = emptyList()
                        }
                    }

                    subDirs.filterNot { listOf("bg", "songs", "soundfonts", "se").contains(it) }
                        .forEach { otherDir ->
                            val otherDirFiles = File(groupDir, otherDir).listFiles()?.map { it.name } ?: emptyList()
                            resultMap[otherDir] = otherDirFiles
                        }

                    resultMap
                } else {
                    emptyMap()
                }
            } else {
                emptyMap()
            }
        } catch (e: SecurityException) {
            emptyMap()
        }
    }

    fun getItemsFromPath(path: String): List<String> {
        return try {
            val externalFilesDir = appContext!!.getExternalFilesDir(null)
            if (externalFilesDir != null) {
                val targetDir = File(externalFilesDir, path)
                if (targetDir.exists() && targetDir.isDirectory) {
                    targetDir.listFiles()?.map { it.name } ?: emptyList()
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    fun getAbsolutePath(relativePath: String): String? {
        val externalFilesDir = appContext?.getExternalFilesDir(null) ?: return null
        return File(externalFilesDir, relativePath).absolutePath
    }

    fun createNewFile(path: String, fileName: String): Boolean {
        return try {
            val externalFilesDir = appContext?.getExternalFilesDir(null)
            if (externalFilesDir != null) {
                val file = File(externalFilesDir, "$path/$fileName")
                file.createNewFile()
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("KaraokeDirectoriesCore", "Error creating file", e)
            false
        }
    }

    fun createNewFolder(path: String, folderName: String): Boolean {
        return try {
            val externalFilesDir = appContext?.getExternalFilesDir(null)
            if (externalFilesDir != null) {
                val folder = File(externalFilesDir, "$path/$folderName")
                folder.mkdirs()
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("KaraokeDirectoriesCore", "Error creating folder", e)
            false
        }
    }

    fun deleteItem(path: String, itemName: String): Boolean {
        return try {
            val externalFilesDir = appContext?.getExternalFilesDir(null)
            if (externalFilesDir != null) {
                val item = File(externalFilesDir, "$path/$itemName")
                if (item.exists()) {
                    if (item.isDirectory) {
                        item.deleteRecursively()
                    } else {
                        item.delete()
                    }
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("KaraokeDirectoriesCore", "Error deleting item", e)
            false
        }
    }

    fun handleFileUpload(path: String, uri: Uri): Boolean {
        return try {
            val inputStream = appContext?.contentResolver?.openInputStream(uri)
            val fileName = getFileNameFromUri(uri) ?: return false

            val externalFilesDir = appContext?.getExternalFilesDir(null)
            if (externalFilesDir != null) {
                val destinationFile = File(externalFilesDir, "$path/$fileName")

                inputStream?.use { input ->
                    destinationFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("KaraokeDirectoriesCore", "Error uploading file", e)
            false
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        return when (uri.scheme) {
            "content" -> {
                val cursor = appContext?.contentResolver?.query(uri, null, null, null, null)
                cursor?.use {
                    val index: Int = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (it.moveToFirst() ) {
                        it.getString(index)
                    } else {
                        null
                    }
                }
            }
            "file" -> File(uri.path).name
            else -> null
        }
    }

    fun getFileOpenIntent(path: String): Intent? {
        return try {
            val absolutePath = getAbsolutePath(path) ?: return null
            val file = File(absolutePath)

            val uri = FileProvider.getUriForFile(
                appContext!!,
                "${appContext!!.packageName}.fileprovider",
                file
            )

            var mimeType = appContext?.contentResolver?.getType(uri)

            if (mimeType == null) {
                mimeType = when {
                    path.endsWith(".json", ignoreCase = true) -> "application/json"
                    path.endsWith(".txt", ignoreCase = true) -> "text/plain"
                    path.endsWith(".csv", ignoreCase = true) -> "text/csv"
                    else -> "*/*"
                }
            }

            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
        } catch (e: Exception) {
            Log.e("KaraokeDirectoriesCore", "Error creating file open intent", e)
            null
        }
    }

    fun renameItem(path: String, oldName: String, newName: String): Boolean {
        return try {
            val externalFilesDir = appContext?.getExternalFilesDir(null)
            if (externalFilesDir != null) {
                val oldFile = File(externalFilesDir, "$path/$oldName")
                val newFile = File(externalFilesDir, "$path/$newName")
                oldFile.renameTo(newFile)
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("KaraokeDirectoriesCore", "Error renaming item", e)
            false
        }
    }

    fun getFileDetails(path: String, itemName: String): Map<String, String> {
        val details = mutableMapOf<String, String>()
        try {
            val externalFilesDir = appContext?.getExternalFilesDir(null)
            if (externalFilesDir != null) {
                val file = File(externalFilesDir, "$path/$itemName")

                details["name"] = file.name
                details["path"] = file.absolutePath

                // Get file extension
                val extension = file.extension.ifEmpty { "folder" }
                details["type"] = if (file.isDirectory) "Folder" else extension.uppercase() + " File"

                // Get MIME type
                val mimeType = if (file.isDirectory) {
                    "folder"
                } else {
                    when (extension.lowercase()) {
                        "jpg", "jpeg", "png", "gif" -> "image/$extension"
                        "mp3", "wav", "ogg" -> "audio/$extension"
                        "mp4", "mkv", "avi" -> "video/$extension"
                        "txt", "csv" -> "text/plain"
                        "json" -> "application/json"
                        "pdf" -> "application/pdf"
                        else -> "application/octet-stream"
                    }
                }
                details["mime"] = mimeType

                // Get size
                val size = if (file.isDirectory) {
                    "Folder"
                } else {
                    val sizeBytes = file.length()
                    when {
                        sizeBytes < 1024 -> "$sizeBytes B"
                        sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
                        else -> "${sizeBytes / (1024 * 1024)} MB"
                    }
                }
                details["size"] = size

                // Get dates
                details["created"] = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(java.util.Date(file.lastModified()))

                details["modified"] = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(java.util.Date(file.lastModified()))
            }
        } catch (e: Exception) {
            Log.e("KaraokeDirectoriesCore", "Error getting file details", e)
        }
        return details
    }

    fun getContext(): Context? {
        return appContext
    }
}