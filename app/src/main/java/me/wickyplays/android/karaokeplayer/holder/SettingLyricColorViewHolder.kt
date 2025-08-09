package me.wickyplays.android.karaokeplayer.holder

import android.view.View
import android.widget.ImageView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import me.wickyplays.android.karaokeplayer.R

class SettingLyricColorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val colorView: View = itemView.findViewById(R.id.colorView)
    private val selectedIndicator: ImageView = itemView.findViewById(R.id.selectedIndicator)

    fun bind(color: String, isSelected: Boolean) {
        colorView.setBackgroundColor(color.toColorInt())
        selectedIndicator.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
    }
}