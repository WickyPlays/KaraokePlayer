package me.wickyplays.android.karaokeplayer.player.manager

import android.view.View
import me.wickyplays.android.karaokeplayer.databinding.ActivityPlayerBinding
import me.wickyplays.android.karaokeplayer.R

class PlayerScoreManager(val binding: ActivityPlayerBinding) {

    fun setScoreVisible(visible: Boolean) {
        binding.playerScore.root.visibility = if (visible) View.VISIBLE else View.GONE
        binding.playerQueueBar.root.visibility = if (visible) View.GONE else View.VISIBLE
        binding.playerLyrics.root.visibility = if (visible) View.GONE else View.VISIBLE
        binding.songSelector.root.visibility = if (visible) View.GONE else View.VISIBLE
    }

    fun setScoreText(score: Int) {
        binding.playerScore.score.text = score.toString()

        binding.playerScore.scoreComment.text = when (score) {
            in 0..0 -> binding.root.context.getString(R.string.player_score_your_voice_cannot_be_heard)
            in 1..50 -> binding.root.context.getString(R.string.player_score_you_need_to_practice_more)
            in 51..70 -> binding.root.context.getString(R.string.player_score_not_bad)
            in 71..90 -> binding.root.context.getString(R.string.player_score_good_job)
            else -> binding.root.context.getString(R.string.player_score_perfect_score)
        }
    }
}