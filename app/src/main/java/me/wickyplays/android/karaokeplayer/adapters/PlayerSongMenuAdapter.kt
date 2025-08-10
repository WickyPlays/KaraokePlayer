package me.wickyplays.android.karaokeplayer.player.manager

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.TextView
import me.wickyplays.android.karaokeplayer.R
import me.wickyplays.android.karaokeplayer.player.obj.Song

class PlayerSongMenuAdapter(
    context: Context,
    private var data: List<Song>,
    private val onSongSelected: (Song) -> Unit
) : BaseAdapter() {
    private val inflater = LayoutInflater.from(context)

    fun updateData(newData: List<Song>) {
        data = newData
        notifyDataSetChanged()
    }

    override fun getCount(): Int = data.size
    override fun getItem(position: Int): Song = data[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: inflater.inflate(R.layout.player_song_item, parent, false)

        val song = getItem(position)

        val numberView = view.findViewById<TextView>(R.id.player_song_menu_item_number)
        val titleView = view.findViewById<TextView>(R.id.player_song_menu_item_title)
        val artistView = view.findViewById<TextView>(R.id.player_song_menu_item_artist)
        val charterView = view.findViewById<TextView>(R.id.player_song_menu_item_charter)
        val addButton = view.findViewById<Button>(R.id.player_song_menu_item_button)

        numberView.text = song.number
        titleView.text = song.title
        artistView.text = song.artist
        charterView.text = song.charter

        addButton.setOnClickListener {
            onSongSelected(song)
        }

        return view
    }
}