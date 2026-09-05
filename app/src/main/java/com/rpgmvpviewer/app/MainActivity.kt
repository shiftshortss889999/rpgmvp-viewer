package com.rpgmvpviewer.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.rpgmvpviewer.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DocFileEntry(val uri: Uri, val name: String)

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var adapter: ImageAdapter

    private var allImages: List<DocFileEntry> = emptyList()
    private var displayedImages: List<DocFileEntry> = emptyList()
    private var currentKey: ByteArray? = null

    private val pickFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) handlePickedFolder(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = getSharedPreferences("rpgmvp_prefs", Context.MODE_PRIVATE)

        setSupportActionBar(binding.toolbar)

        binding.recyclerView.layoutManager = GridLayoutManager(this, calculateSpanCount())
        adapter = ImageAdapter(emptyList(), null) { position -> openViewer(position) }
        binding.recyclerView.adapter = adapter

        binding.btnSelectFolder.setOnClickListener { pickFolderLauncher.launch(null) }
        binding.emptyState.visibility = View.VISIBLE

        val savedUriString = prefs.getString(KEY_LAST_FOLDER, null)
        if (savedUriString != null) {
            val uri = Uri.parse(savedUriString)
            val hasPermission = contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission
            }
            if (hasPermission) handlePickedFolder(uri)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.search_hint)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterImages(newText.orEmpty())
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_change_folder) {
            pickFolderLauncher.launch(null)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun calculateSpanCount(): Int {
        val widthDp = resources.configuration.screenWidthDp
        return (widthDp / 110).coerceAtLeast(2)
    }

    private fun handlePickedFolder(uri: Uri) {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        prefs.edit().putString(KEY_LAST_FOLDER, uri.toString()).apply()

        setLoading(true)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                FileScanner.scan(applicationContext, uri)
            }
            setLoading(false)
            allImages = result.imageFiles.map { DocFileEntry(it.uri, it.name ?: "") }
            displayedImages = allImages
            currentKey = result.encryptionKey

            adapter.updateData(displayedImages, currentKey)
            updateStatusText()
        }
    }

    private fun filterImages(query: String) {
        displayedImages = if (query.isBlank()) {
            allImages
        } else {
            allImages.filter { it.name.contains(query, ignoreCase = true) }
        }
        adapter.updateData(displayedImages, currentKey)
        updateEmptyState()
    }

    private fun updateStatusText() {
        updateEmptyState()
        val keyStatus =
            if (currentKey != null) getString(R.string.key_found) else getString(R.string.key_not_found)
        binding.statusText.text = getString(R.string.status_format, allImages.size, keyStatus)
    }

    private fun updateEmptyState() {
        binding.emptyState.visibility = if (displayedImages.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSelectFolder.isEnabled = !loading
    }

    private fun openViewer(position: Int) {
        val intent = Intent(this, ViewerActivity::class.java).apply {
            putParcelableArrayListExtra(
                ViewerActivity.EXTRA_URIS,
                ArrayList(displayedImages.map { it.uri })
            )
            putExtra(ViewerActivity.EXTRA_KEY, currentKey)
            putExtra(ViewerActivity.EXTRA_START_POS, position)
        }
        startActivity(intent)
    }

    companion object {
        private const val KEY_LAST_FOLDER = "last_folder_uri"
    }
}
