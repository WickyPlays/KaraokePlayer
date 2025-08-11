package me.wickyplays.android.karaokeplayer.cores.player.obj

data class PianoKey(
    private var isBlack: Boolean,
    private var noteNumber: Int,
    private var nodes: List<JudgementNode> = emptyList()
)