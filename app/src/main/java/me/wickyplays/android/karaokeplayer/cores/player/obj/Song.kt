package me.wickyplays.android.karaokeplayer.cores.player.obj

import me.wickyplays.android.karaokeplayer.cores.player.enums.SongType

class Song {
    var number: String = ""
    var parentFolder: String = ""
    var title: String = ""
    var titleTranslit: String? = null
    var artist: String = ""
    var charter: String = ""
    var lyricist: String = ""
    var songPath: String = ""
    var bgPath: String? = null
    var lyricPath: String? = null
    var judgementPath: String? = null
    var fileBuffer: ByteArray? = null
    var songType: SongType = SongType.NONE

    constructor()

    constructor(init: Song.() -> Unit) {
        init()
    }

    fun setNumber(value: String): Song {
        number = value
        return this
    }

    fun setParentFolder(value: String): Song {
        parentFolder = value
        return this
    }

    fun setTitle(value: String): Song {
        title = value
        return this
    }

    fun setTitleTranslit(value: String?): Song {
        titleTranslit = value
        return this
    }

    fun setArtist(value: String): Song {
        artist = value
        return this
    }

    fun setCharter(value: String): Song {
        charter = value
        return this
    }

    fun setLyricist(value: String): Song {
        lyricist = value
        return this
    }

    fun setSongPath(value: String): Song {
        songPath = value
        return this
    }

    fun setBgPath(value: String?): Song {
        bgPath = value
        return this
    }

    fun setLyricPath(value: String?): Song {
        lyricPath = value
        return this
    }

    fun setJudgementPath(value: String?): Song {
        judgementPath = value
        return this
    }

    fun setFileBuffer(value: ByteArray?): Song {
        fileBuffer = value
        return this
    }

    fun setSongType(value: SongType): Song {
        songType = value
        return this
    }

    override fun toString(): String {
        return "$number - $title - $artist"
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "number" to number,
            "parentFolder" to parentFolder,
            "title" to title,
            "titleTranslit" to titleTranslit,
            "artist" to artist,
            "charter" to charter,
            "lyricist" to lyricist,
            "songPath" to songPath,
            "lyricPath" to lyricPath,
            "judgementPath" to judgementPath,
            "songType" to songType
        )
    }

    data class SongConfig(
        val number: String,
        val title: String,
        val title_translit: String?,
        val artist: String,
        val charter: String,
        val lyricist: String,
        val song_path: String,
        val lyric_path: String,
        val bg_path: String?,
        val judgement_path: String
    )
}