package me.wickyplays.android.karaokeplayer.cores.directories

import android.content.Context
import java.io.File

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

                    // Create a map with the 4 predefined categories
                    val resultMap = mutableMapOf<String, List<String>>()

                    // Check each predefined category and add if directory exists
                    listOf("bg", "songs", "soundfonts", "se").forEach { category ->
                        val categoryDir = File(groupDir, category)
                        if (categoryDir.exists() && categoryDir.isDirectory) {
                            val items = categoryDir.listFiles()?.map { it.name } ?: emptyList()
                            resultMap[category] = items
                        } else {
                            resultMap[category] = emptyList()
                        }
                    }

                    // Also include any other directories that might exist in the group
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

    fun getAllDirectoriesCombined(): List<String> {
        return getAllDirectories() + getAllExternalDirectories()
    }

    fun getContext(): Context? {
        return appContext
    }
}