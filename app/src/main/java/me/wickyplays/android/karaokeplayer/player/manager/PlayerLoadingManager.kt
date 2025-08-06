package me.wickyplays.android.karaokeplayer.player.manager

import android.content.Context
import android.view.View
import me.wickyplays.android.karaokeplayer.databinding.ActivityPlayerBinding

class PlayerLoadingManager(private val context: Context, private val binding: ActivityPlayerBinding) {

    fun setLoadingState(state: Boolean, songTitle: String = "") {
        val loadingView = binding.playerLoading.root
        loadingView.visibility = if (state) View.VISIBLE else View.GONE
        binding.mainContentLayout.visibility = if (state) View.GONE else View.VISIBLE
        setLoadingSubtitle(songTitle)
    }

    fun setLoadingSubtitle(text: String) {
        binding.playerLoading.loadingSubtitle.text = text
    }
}