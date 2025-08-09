package me.wickyplays.android.karaokeplayer.player.obj

data class JudgementNode(
    val n: Int,         // note number
    val s: Double, // start time in seconds
    val e: Double,   // end time in seconds
    val hit: Boolean     // whether the note was hit
)