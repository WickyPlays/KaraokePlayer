package me.wickyplays.android.karaokeplayer.player.manager

import android.content.Context
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.TextView
import me.wickyplays.android.karaokeplayer.R
import me.wickyplays.android.karaokeplayer.databinding.ActivityPlayerBinding
import me.wickyplays.android.karaokeplayer.player.obj.Song

class PlayerSongMenuManager(
    private val context: Context,
    private val binding: ActivityPlayerBinding
) {
    private var isMenuVisible = false
    private lateinit var songMenuAdapter: SongMenuAdapter
    private var songList = mutableListOf<Song>()
    private var onSongSelected: ((Song) -> Unit)? = null


    fun initialize(songs: List<Song>, songSelectedCallback: (Song) -> Unit) {
        this.songList = songs.toMutableList()
        this.onSongSelected = songSelectedCallback
        this.binding.playerSongMenu.playerSongSearchClose.setOnClickListener {
            toggleMenuVisibility()
        }
        setupSongMenu()
    }

    fun toggleMenuVisibility() {
        isMenuVisible = !isMenuVisible
        binding.playerSongMenu.root.visibility = if (isMenuVisible) View.VISIBLE else View.GONE

        if (isMenuVisible) {
            songMenuAdapter.updateData(songList)
            binding.playerSongMenu.playerSongSearchInput.requestFocus()
        }
    }

    fun isMenuVisible(): Boolean = isMenuVisible

    fun addSong(song: Song) {
        songList.add(song)
        songMenuAdapter.updateData(songList)
    }

    private fun setupSongMenu() {
        binding.playerSongMenu.playerSongList.adapter = SongMenuAdapter(context, songList).also {
            songMenuAdapter = it
        }

        binding.playerSongMenu.playerSongSearchButton.setOnClickListener {
            performSearch()
        }

        binding.playerSongMenu.playerSongSearchInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                performSearch()
                true
            } else {
                false
            }
        }

        binding.playerSongMenu.playerSongList.setOnItemClickListener { _, _, position, _ ->
            val selectedSong = songMenuAdapter.getItem(position)
            onSongSelected?.invoke(selectedSong)
            toggleMenuVisibility()
        }
    }

    private fun performSearch() {
        val query = binding.playerSongMenu.playerSongSearchInput.text.toString().trim()
        val filteredList = if (query.isEmpty()) {
            songList
        } else {
            songList.filter { song ->
                song.titleTranslit?.contains(query, true) == true ||
                        song.title.contains(query, true) ||
                        song.artist.contains(query, true) ||
                        song.number.contains(query)
            }
        }
        songMenuAdapter.updateData(filteredList)
    }

    private inner class SongMenuAdapter(
        context: Context,
        private var data: List<Song>
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
                onSongSelected?.invoke(song)
                toggleMenuVisibility()
            }

            return view
        }
    }
}