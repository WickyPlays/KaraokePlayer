package me.wickyplays.android.karaokeplayer.player.obj

data class PianoKey(
    private var isBlack: Boolean,
    private var noteNumber: Int,
    private var nodes: List<JudgementNode> = emptyList()
)