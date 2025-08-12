package me.wickyplays.android.karaokeplayer.fragements

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import me.wickyplays.android.karaokeplayer.R
import me.wickyplays.android.karaokeplayer.cores.directories.KaraokeDirectoriesCore

class DirectoriesFragment : Fragment() {

    private lateinit var core: KaraokeDirectoriesCore
    private lateinit var leftRecyclerView: RecyclerView
    private lateinit var rightRecyclerView: RecyclerView
    private lateinit var spinner: Spinner
    private lateinit var backButtonLayout: View
    private lateinit var backButton: ImageButton

    private var currentGroup: String = ""
    private var currentPath: String = ""
    private val pathHistory = mutableListOf<String>()
    private val mainCategories = listOf("bg", "songs", "soundfonts", "se")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_directories_dir, container, false)

        core = KaraokeDirectoriesCore.getInstance()
        spinner = view.findViewById(R.id.groupSpinner)
        leftRecyclerView = view.findViewById(R.id.folderRecyclerView)
        rightRecyclerView = view.findViewById(R.id.directoryContent)
        backButtonLayout = view.findViewById(R.id.backButtonLayout)
        backButton = view.findViewById(R.id.backButton)

        setupViews()
        return view
    }

    private fun setupViews() {
        val directories: List<String> = core.getAllExternalDirectories()
        val dataAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, directories)
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = dataAdapter

        // Setup left RecyclerView (categories)
        leftRecyclerView.layoutManager = LinearLayoutManager(context)
        leftRecyclerView.addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL))

        // Setup right RecyclerView (contents)
        rightRecyclerView.layoutManager = LinearLayoutManager(context)
        rightRecyclerView.addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL))

        // Hide back button initially
        backButtonLayout.visibility = View.GONE

        // Set initial data
        if (directories.isNotEmpty()) {
            currentGroup = directories[0]
            currentPath = currentGroup
            updateCategoryList()
        }

        // Spinner selection listener
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentGroup = parent?.getItemAtPosition(position).toString()
                currentPath = currentGroup
                pathHistory.clear()
                backButtonLayout.visibility = View.GONE
                updateCategoryList()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        backButton.setOnClickListener {
            navigateBack()
        }
    }

    private fun navigateBack() {
        if (pathHistory.isNotEmpty()) {
            currentPath = pathHistory.removeAt(pathHistory.lastIndex)
            updateContentList(currentPath)

            if (isMainCategoryLevel(currentPath)) {
                backButtonLayout.visibility = View.GONE
            }
        }
    }

    private fun isMainCategoryLevel(path: String): Boolean {
        val parts = path.split('/')
        return parts.size == 2 && mainCategories.contains(parts[1])
    }

    private fun updateCategoryList() {
        val categories = core.getItemsFromGroup(currentGroup).keys.toList()
        leftRecyclerView.adapter = CategoryAdapter(categories) { category ->
            currentPath = "$currentGroup/$category"
            pathHistory.clear()
            updateContentList(currentPath)

            // Don't show back button for main categories
            backButtonLayout.visibility = View.GONE
        }
    }

    private fun updateContentList(path: String) {
        val items = core.getItemsFromPath(path).sorted()
        rightRecyclerView.adapter = ContentAdapter(path, items) { item ->
            val newPath = "$path/$item"
            if (core.getItemsFromPath(newPath).isNotEmpty()) {
                pathHistory.add(path)
                currentPath = newPath

                val isInsideMainCategory = path.split('/').let { parts ->
                    parts.size >= 2 && mainCategories.contains(parts[1])
                }
                backButtonLayout.visibility = if (isInsideMainCategory) View.VISIBLE else View.GONE

                updateContentList(newPath)
            }
        }
    }

    private inner class CategoryAdapter(
        private val categories: List<String>,
        private val onItemClick: (String) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fragment_directories_dir_itemmenu, parent, false)
            return ViewHolder(view)
        }


        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(categories[position])
        }

        override fun getItemCount() = categories.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            fun bind(category: String) {
                val textView = itemView.findViewById<TextView>(R.id.directoryLabel)
                val iconView = itemView.findViewById<ImageView>(R.id.directoryIcon)

                textView.text = category

                val iconRes = when (category) {
                    "bg" -> R.drawable.image_24px
                    "songs" -> R.drawable.music_note_24px
                    "soundfonts" -> R.drawable.audio_file_24px
                    "se" -> R.drawable.brand_awareness_24px
                    else -> R.drawable.folder_24px
                }
                iconView.setImageResource(iconRes)

                itemView.setOnClickListener { onItemClick(category) }
            }
        }
    }

    private inner class ContentAdapter(
        private val currentPath: String,
        private val items: List<String>,
        private val onItemClick: (String) -> Unit
    ) : RecyclerView.Adapter<ContentAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fragment_directories_content_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            fun bind(item: String) {
                val textView = itemView.findViewById<TextView>(R.id.itemName)
                val iconView = itemView.findViewById<ImageView>(R.id.itemIcon)

                textView.text = item

                // Get the full path to determine if it's a directory or file
                val fullPath = "$currentPath/$item"
                val isDirectory = core.getItemsFromPath(fullPath).isNotEmpty()

                val iconRes = when {
                    isDirectory -> R.drawable.folder_24px
                    item.endsWith(".mp3") || item.endsWith(".wav") -> R.drawable.music_note_24px
                    item.endsWith(".jpg") || item.endsWith(".png") || item.endsWith(".jpeg") -> R.drawable.image_24px
                    item.endsWith(".sf2") -> R.drawable.audio_file_24px
                    item.endsWith(".mid") || item.endsWith(".kar") -> R.drawable.lyrics_24px
                    item.endsWith(".json") -> R.drawable.data_object_24px
                    else -> R.drawable.description_24px
                }
                iconView.setImageResource(iconRes)

                itemView.setOnClickListener {
                    if (isDirectory) {
                        onItemClick(item)
                    }
                    // Do nothing if it's a file (for now)
                }
            }
        }
    }
}