package me.wickyplays.android.karaokeplayer.cores.directories

data class DirectoryItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: Long = 0
)