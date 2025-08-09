package me.wickyplays.android.karaokeplayer.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import me.wickyplays.android.karaokeplayer.R
import me.wickyplays.android.karaokeplayer.holder.SettingLyricColorViewHolder

class SettingLyricColorAdapter(
    private val colors: List<String>,
    private var selectedPosition: Int,
    private val onColorSelected: (Int) -> Unit
) : RecyclerView.Adapter<SettingLyricColorViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingLyricColorViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_color, parent, false)
        return SettingLyricColorViewHolder(view)
    }

    override fun onBindViewHolder(holder: SettingLyricColorViewHolder, position: Int) {
        holder.bind(colors[position], position == selectedPosition)
        holder.itemView.setOnClickListener {
            val previousSelected = selectedPosition
            selectedPosition = position
            notifyItemChanged(previousSelected)
            notifyItemChanged(selectedPosition)
            onColorSelected(position)
        }
    }

    override fun getItemCount() = colors.size
}