package me.wickyplays.android.karaokeplayer.player.manager

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import me.wickyplays.android.karaokeplayer.activities.PlayerActivity
import me.wickyplays.android.karaokeplayer.player.KaraokePlayerCore
import me.wickyplays.android.karaokeplayer.player.obj.Song
import me.wickyplays.android.karaokeplayer.player.enums.SongType
import java.io.File

class PlayerDirectoryManager(context: Context) {
    private val appDataDir: File = context.getExternalFilesDir(null) ?: context.filesDir
    private val defaultTemplateDir: File = File(appDataDir, "default")
    private val defaultBgDir: File = File(defaultTemplateDir, "bg")
    private val defaultSongsDir: File = File(defaultTemplateDir, "songs")
    private val defaultSeDir: File = File(defaultTemplateDir, "se")
    private val defaultSoundfontsDir: File = File(defaultTemplateDir, "soundfonts")
    private val gson = Gson()

    public fun setup() {
        createDirectories()
        scanSongDirs()
    }

    private fun createDirectories() {
        Log.d("Player", "Creating directories at $defaultTemplateDir")
        createDirectory(defaultTemplateDir)
        createDirectory(defaultBgDir)
        createDirectory(defaultSongsDir)
        createDirectory(defaultSeDir)
        createDirectory(defaultSoundfontsDir)
    }

    private fun createDirectory(dir: File) {
        if (!dir.exists()) {
            dir.mkdirs()
        }
    }

    private fun scanBackgroundFolders() {
        // Empty as requested
    }

    private fun scanSongDirs() {
        Log.d("Player", "Scanning song folders at $defaultSongsDir")
        defaultSongsDir.listFiles()?.filter { it.isDirectory }?.forEach { folder ->
            val configFile = File(folder, "config.json")
            if (configFile.exists()) {
                try {
                    val configContent = configFile.readText()
                    val config = gson.fromJson(configContent, Song.SongConfig::class.java)

                    val song = Song {
                        number = config.number
                        title = config.title
                        titleTranslit = config.title_translit
                        artist = config.artist
                        charter = config.charter
                        lyricist = config.lyricist
                        songPath = File(folder, config.song_path).absolutePath
                        lyricPath = File(folder, config.lyric_path).absolutePath
                        bgPath = config.bg_path?.let { File(folder, it).absolutePath }
                        judgementPath = File(folder, config.judgement_path).absolutePath
                        parentFolder = folder.absolutePath
                        songType = SongType.MIDI
                    }

                    KaraokePlayerCore.instance.addSong(song)
                } catch (e: Exception) {
                    Log.e("Player", "Error reading config in ${folder.name}: ${e.message}")
                }
            }
        }
        Log.d("Player", "Total number of songs: ${KaraokePlayerCore.instance.getSongList().size}")
    }

    fun getAssetsFromTemplate(template: String = "default"): Map<String, File> {
        val templateDir = File(appDataDir, template)
        createDirectory(templateDir)

        return mapOf(
            "bg" to File(templateDir, "bg").apply { createDirectory(this) },
            "songs" to File(templateDir, "songs").apply { createDirectory(this) },
            "se" to File(templateDir, "se").apply { createDirectory(this) },
            "soundfonts" to File(templateDir, "soundfonts").apply { createDirectory(this) }
        )
    }

    fun getBackgroundFromBgDir(): Uri? {
        val bgDir = getBackgroundsDir()
        if (!bgDir.exists() || !bgDir.isDirectory) return null

        val supportedExtensions = listOf(
            // Image formats
            "jpg", "jpeg", "png", "gif", "bmp", "webp",
            // Video formats
            "mp4", "mkv", "webm", "avi", "mov", "3gp", "mpeg", "mpg"
        )

        val firstBgFile = bgDir.listFiles()
            ?.filter { it.isFile }
            ?.firstOrNull { file ->
                val extension = file.extension.lowercase()
                extension in supportedExtensions
            }

        return firstBgFile?.let { Uri.fromFile(it) }
    }

    fun getBackgroundsDir(): File = defaultBgDir
    fun getSongsDir(): File = defaultSongsDir
    fun getSoundEffectsDir(): File = defaultSeDir
    fun getSoundfontsDir(): File = defaultSoundfontsDir
}