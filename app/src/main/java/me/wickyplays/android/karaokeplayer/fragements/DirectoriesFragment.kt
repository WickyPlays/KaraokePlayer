package me.wickyplays.android.karaokeplayer.fragements

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import me.wickyplays.android.karaokeplayer.R
import me.wickyplays.android.karaokeplayer.cores.directories.KaraokeDirectoriesCore

class DirectoryFragment : Fragment() {

    private lateinit var core: KaraokeDirectoriesCore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_directories_dir, container, false)
        val recyclerView: RecyclerView = view.findViewById(R.id.folderRecyclerView)

        recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        recyclerView.addItemDecoration(
            DividerItemDecoration(
                context,
                LinearLayoutManager.VERTICAL
            )
        )

        core = KaraokeDirectoriesCore.getInstance()
        val directories: List<String> = core.getAllExternalDirectories()
        val spinner = view.findViewById<Spinner>(R.id.groupSpinner)

        val dataAdapter: ArrayAdapter<String?> =
            ArrayAdapter<String?>(core.getContext()!!, android.R.layout.simple_spinner_item, directories)
        spinner.adapter = dataAdapter

        val folderList = listOf("bg", "songs", "soundfonts", "se")
        recyclerView.adapter = DirectoryItemMenuAdapter(folderList)

        return view
    }


    private inner class DirectoryItemMenuAdapter(private val items: List<String>) :
        RecyclerView.Adapter<DirectoryItemMenuAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fragment_directories_dir_itemmenu, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int {
            return items.size
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            fun bind(item: String) {
                val textView = itemView.findViewById<TextView>(R.id.directoryLabel)
                textView.text = item
            }
        }
    }
}