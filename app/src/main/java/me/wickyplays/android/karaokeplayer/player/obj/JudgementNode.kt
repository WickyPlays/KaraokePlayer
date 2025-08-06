package me.wickyplays.android.karaokeplayer.player.obj

data class JudgementNode(
    val node: Int,         // note number
    val startTime: Double, // start time in seconds
    val endTime: Double,   // end time in seconds
    val isHit: Boolean     // whether the note was hit
)