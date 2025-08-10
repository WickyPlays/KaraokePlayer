package me.wickyplays.android.karaokeplayer.player.manager

import android.content.Context
import android.view.KeyEvent
import android.view.View
import me.wickyplays.android.karaokeplayer.databinding.ActivityPlayerBinding
import me.wickyplays.android.karaokeplayer.player.obj.Song

class PlayerSongMenuManager(
    private val context: Context,
    private val binding: ActivityPlayerBinding
) {
    private var isMenuVisible = false
    private lateinit var songMenuAdapter: PlayerSongMenuAdapter
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

    fun addSong(song: Song) {
        songList.add(song)
        songMenuAdapter.updateData(songList)
    }

    private fun setupSongMenu() {
        binding.playerSongMenu.playerSongList.adapter = PlayerSongMenuAdapter(
            context,
            songList,
            { song ->
                onSongSelected?.invoke(song)
                toggleMenuVisibility()
            }
        ).also {
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
}