package me.wickyplays.android.karaokeplayer.fragements

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import me.wickyplays.android.karaokeplayer.R
import me.wickyplays.android.karaokeplayer.cores.directories.KaraokeDirectoriesCore

class DirectoriesFragment : Fragment() {

    private companion object {
        const val REQUEST_CODE_UPLOAD_FILE = 1001
        const val REQUEST_CODE_STORAGE_PERMISSION = 1002
    }

    private lateinit var core: KaraokeDirectoriesCore
    private lateinit var leftRecyclerView: RecyclerView
    private lateinit var rightRecyclerView: RecyclerView
    private lateinit var spinner: Spinner
    private lateinit var backButtonLayout: View
    private lateinit var backButton: ImageButton
    private lateinit var newButton: Button
    private lateinit var uploadButton: Button

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
        newButton = view.findViewById(R.id.newButton)
        uploadButton = view.findViewById(R.id.uploadButton)

        setupViews()
        return view
    }

    private fun setupViews() {
        val directories: List<String> = core.getAllExternalDirectories()
        val dataAdapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, directories)
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = dataAdapter

        leftRecyclerView.layoutManager = LinearLayoutManager(context)
        leftRecyclerView.addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL))
        rightRecyclerView.layoutManager = LinearLayoutManager(context)
        rightRecyclerView.addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL))

        backButtonLayout.visibility = View.GONE

        if (directories.isNotEmpty()) {
            currentGroup = directories[0]
            currentPath = currentGroup
            updateCategoryList()
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentGroup = parent?.getItemAtPosition(position).toString()
                currentPath = currentGroup
                pathHistory.clear()
                backButtonLayout.visibility = View.GONE
                updateCategoryList()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        backButton.setOnClickListener { navigateBack() }
        newButton.setOnClickListener { showNewOptionsMenu(it) }
        uploadButton.setOnClickListener { checkStoragePermissionAndUpload() }
    }

    private fun showNewOptionsMenu(view: View) {
        val popup = PopupMenu(requireContext(), view)
        popup.menuInflater.inflate(R.menu.directory_options_menu, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_new_file -> {
                    showCreateFileDialog()
                    true
                }
                R.id.menu_new_folder -> {
                    showCreateFolderDialog()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showCreateFileDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.fragment_directories_dialog_createitem, null)
        val editText = dialogView.findViewById<EditText>(R.id.itemNameEditText)

        AlertDialog.Builder(requireContext())
            .setTitle("New File")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val fileName = editText.text.toString().trim()
                if (fileName.isNotEmpty()) {
                    if (core.createNewFile(currentPath, fileName)) {
                        updateContentList(currentPath)
                        Toast.makeText(requireContext(), "File created successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "File already exists or couldn't be created", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Please enter a file name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCreateFolderDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.fragment_directories_dialog_createitem, null)
        val editText = dialogView.findViewById<EditText>(R.id.itemNameEditText)

        AlertDialog.Builder(requireContext())
            .setTitle("New Folder")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val folderName = editText.text.toString().trim()
                if (folderName.isNotEmpty()) {
                    if (core.createNewFolder(currentPath, folderName)) {
                        updateContentList(currentPath)
                        Toast.makeText(requireContext(), "Folder created successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Folder already exists or couldn't be created", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Please enter a folder name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameDialog(itemName: String) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.fragment_directories_dialog_rename, null)
        val editText = dialogView.findViewById<EditText>(R.id.renameEditText)
        editText.setText(itemName)

        AlertDialog.Builder(requireContext())
            .setTitle("Rename Item")
            .setView(dialogView)
            .setPositiveButton("Rename") { _, _ ->
                val newName = editText.text.toString().trim()
                when {
                    newName.isEmpty() -> Toast.makeText(requireContext(), "Please enter a new name", Toast.LENGTH_SHORT).show()
                    newName == itemName -> Toast.makeText(requireContext(), "Name unchanged", Toast.LENGTH_SHORT).show()
                    else -> {
                        if (core.renameItem(currentPath, itemName, newName)) {
                            updateContentList(currentPath)
                            Toast.makeText(requireContext(), "Item renamed successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "Error renaming item", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDetailsDialog(itemName: String) {
        val details = core.getFileDetails(currentPath, itemName)
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.fragment_directories_dialog_details, null)

        with(dialogView) {
            findViewById<TextView>(R.id.detailName).text = details["name"] ?: "Unknown"
            findViewById<TextView>(R.id.detailType).text = details["type"] ?: "Unknown"
            findViewById<TextView>(R.id.detailSize).text = details["size"] ?: "Unknown"
            findViewById<TextView>(R.id.detailCreated).text = details["created"] ?: "Unknown"
            findViewById<TextView>(R.id.detailModified).text = details["modified"] ?: "Unknown"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("File Details")
            .setView(dialogView)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showDeleteConfirmation(itemName: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Item")
            .setMessage("Are you sure you want to delete '$itemName'?")
            .setPositiveButton("Delete") { _, _ ->
                if (core.deleteItem(currentPath, itemName)) {
                    updateContentList(currentPath)
                    Toast.makeText(requireContext(), "Item deleted successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Error deleting item", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
            backButtonLayout.visibility = View.GONE
        }
    }

    private fun updateContentList(path: String) {
        val items = core.getItemsFromPath(path).sorted()
        rightRecyclerView.adapter = ItemContentAdapter(path, items) { item ->
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

    private fun checkStoragePermissionAndUpload() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startFileUpload()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_CODE_STORAGE_PERMISSION
            )
        }
    }

    private fun startFileUpload() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        }
        startActivityForResult(intent, REQUEST_CODE_UPLOAD_FILE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_CODE_STORAGE_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startFileUpload()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Storage permission is required to upload files",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_UPLOAD_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                if (core.handleFileUpload(currentPath, uri)) {
                    updateContentList(currentPath)
                    Toast.makeText(requireContext(), "File uploaded successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Error uploading file", Toast.LENGTH_SHORT).show()
                }
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

    private inner class ItemContentAdapter(
        private val currentPath: String,
        private val items: List<String>,
        private val onItemClick: (String) -> Unit
    ) : RecyclerView.Adapter<ItemContentAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fragment_directories_content_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnCreateContextMenuListener {
            private lateinit var currentItem: String

            init {
                itemView.setOnCreateContextMenuListener(this)
                itemView.setOnLongClickListener {
                    itemView.showContextMenu()
                    true
                }
            }

            fun bind(item: String) {
                currentItem = item
                val textView = itemView.findViewById<TextView>(R.id.itemName)
                val iconView = itemView.findViewById<ImageView>(R.id.itemIcon)

                textView.text = item

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
                    } else {
                        val intent = core.getFileOpenIntent(fullPath)
                        if (intent != null) {
                            startActivity(Intent.createChooser(intent, "Open with"))
                        } else {
                            Toast.makeText(requireContext(), "Cannot open file", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
                MenuInflater(v.context).inflate(R.menu.item_content_menu, menu)

                menu.findItem(R.id.menu_rename).setOnMenuItemClickListener {
                    showRenameDialog(currentItem)
                    true
                }

                menu.findItem(R.id.menu_details).setOnMenuItemClickListener {
                    showDetailsDialog(currentItem)
                    true
                }

                menu.findItem(R.id.menu_delete).setOnMenuItemClickListener {
                    showDeleteConfirmation(currentItem)
                    true
                }
            }
        }
    }
}