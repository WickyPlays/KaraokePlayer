package me.wickyplays.android.karaokeplayer.cores.player.manager

import me.wickyplays.android.karaokeplayer.databinding.ActivityPlayerBinding

class PlayerSongQueueManager(private val binding: ActivityPlayerBinding) {

    fun setSongMetaText(text: String) {
        binding.playerQueueBar.metaText.text = text
    }
}