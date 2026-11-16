package com.workcamera.app

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

class GalleryActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var countText: TextView
    private lateinit var emptyView: View
    private lateinit var emptyDesc: TextView
    private lateinit var goShootBtn: TextView
    private lateinit var chipsContainer: LinearLayout
    private lateinit var chipsScroll: HorizontalScrollView
    private lateinit var editSearch: EditText
    private lateinit var clearBtn: View
    private lateinit var dateStartField: TextView
    private lateinit var dateEndField: TextView
    private lateinit var resetFilter: TextView
    private lateinit var cancelSelectBtn: TextView
    private lateinit var selectionBar: View
    private lateinit var selectionCount: TextView
    private lateinit var selectionShare: TextView
    private lateinit var selectionDelete: TextView

    private lateinit var adapter: PhotoAdapter
    private lateinit var loader: ThumbLoader
    private lateinit var repository: PhotoRepository

    private val photos = ArrayList<WorkPhoto>()
    private var startDate: LocalDate? = null
    private var endDate: LocalDate? = null
    private var queryText = ""
    private var firstResume = true

    private val selected = LinkedHashSet<String>()
    private var selectionMode = false

    private val io = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.CHINA)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { loadPhotos() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        bindViews()
        setupGlyphs()
        setupRecycler()
        setupSearch()
        setupDateFilter()
        setupSelection()
        ensurePermissions()
    }

    private fun bindViews() {
        recycler = findViewById(R.id.recycler)
        countText = findViewById(R.id.countText)
        emptyView = findViewById(R.id.emptyView)
        emptyDesc = findViewById(R.id.emptyDesc)
        goShootBtn = findViewById(R.id.goShootBtn)
        chipsContainer = findViewById(R.id.chipsContainer)
        chipsScroll = findViewById(R.id.chipsScroll)
        editSearch = findViewById(R.id.editSearch)
        clearBtn = findViewById(R.id.clearBtn)
        dateStartField = findViewById(R.id.dateStartField)
        dateEndField = findViewById(R.id.dateEndField)
        resetFilter = findViewById(R.id.resetFilter)
        cancelSelectBtn = findViewById(R.id.cancelSelectBtn)
        selectionBar = findViewById(R.id.selectionBar)
        selectionCount = findViewById(R.id.selectionCount)
        selectionShare = findViewById(R.id.selectionShare)
        selectionDelete = findViewById(R.id.selectionDelete)
    }

    private fun setupGlyphs() {
        findViewById<GlyphView>(R.id.searchGlyph).apply {
            style = GlyphView.Style.SEARCH
            tint = ContextCompat.getColor(this@GalleryActivity, R.color.ink_faint)
        }
        findViewById<GlyphView>(R.id.clearGlyph).apply {
            style = GlyphView.Style.CLOSE
            tint = ContextCompat.getColor(this@GalleryActivity, R.color.ink_faint)
        }
        findViewById<GlyphView>(R.id.emptyGlyph).apply {
            style = GlyphView.Style.GALLERY
            tint = ContextCompat.getColor(this@GalleryActivity, R.color.ink_faint)
        }
    }

    private fun setupRecycler() {
        loader = ThumbLoader(this)
        repository = PhotoRepository(this)
        adapter = PhotoAdapter(
            loader,
            onClick = { photo ->
                if (selectionMode) toggleSelection(photo) else openPhoto(photo)
            },
            onLongClick = { photo -> enterSelection(photo) },
            isSelected = { photo -> selected.contains(photo.name) }
        )
        recycler.layoutManager = GridLayoutManager(this, 3)
        recycler.adapter = adapter
    }

    private fun setupSelection() {
        cancelSelectBtn.setOnClickListener { exitSelection() }
        selectionShare.setOnClickListener { shareSelected() }
        selectionDelete.setOnClickListener { confirmDeleteSelected() }
    }

    private fun enterSelection(photo: WorkPhoto) {
        if (!selectionMode) {
            selectionMode = true
            adapter.selectionMode = true
            cancelSelectBtn.visibility = View.VISIBLE
            selectionBar.visibility = View.VISIBLE
            chipsScroll.visibility = View.GONE
        }
        selected.add(photo.name)
        updateSelectionUi()
    }

    private fun toggleSelection(photo: WorkPhoto) {
        if (selected.contains(photo.name)) {
            selected.remove(photo.name)
        } else {
            selected.add(photo.name)
        }
        if (selected.isEmpty()) {
            exitSelection()
        } else {
            updateSelectionUi()
        }
    }

    private fun updateSelectionUi() {
        selectionCount.text = getString(R.string.selection_count, selected.size)
        adapter.notifySelectionChanged()
    }

    private fun exitSelection() {
        selectionMode = false
        selected.clear()
        adapter.selectionMode = false
        cancelSelectBtn.visibility = View.GONE
        selectionBar.visibility = View.GONE
        chipsScroll.visibility = if (chipsContainer.childCount > 0) View.VISIBLE else View.GONE
    }

    private fun selectedPhotos(): List<WorkPhoto> = photos.filter { selected.contains(it.name) }

    private fun confirmDeleteSelected() {
        val targets = selectedPhotos()
        if (targets.isEmpty()) return
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete_selected_confirm, targets.size))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> deleteSelected(targets) }
            .show()
    }

    private fun deleteSelected(targets: List<WorkPhoto>) {
        io.execute {
            var deleted = 0
            var failed = 0
            for (photo in targets) {
                if (repository.delete(photo)) deleted++ else failed++
            }
            mainHandler.post {
                if (selectionMode) exitSelection()
                loadPhotos()
                val message = if (failed > 0) {
                    getString(R.string.delete_some_failed, failed)
                } else {
                    getString(R.string.delete_selected_done, deleted)
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareSelected() {
        val targets = selectedPhotos()
        if (targets.isEmpty()) return
        Toast.makeText(this, "正在准备分享…", Toast.LENGTH_SHORT).show()
        io.execute {
            val uris = PhotoSharer.copyToShare(this, targets)
            mainHandler.post {
                val launched = PhotoSharer.launchChooser(this, uris)
                if (!launched) {
                    Toast.makeText(this, R.string.share_failed, Toast.LENGTH_SHORT).show()
                } else if (selectionMode) {
                    exitSelection()
                }
            }
        }
    }

    private fun sharePhoto(photo: WorkPhoto) {
        Toast.makeText(this, "正在准备分享…", Toast.LENGTH_SHORT).show()
        io.execute {
            val uris = PhotoSharer.copyToShare(this, listOf(photo))
            mainHandler.post {
                if (!PhotoSharer.launchChooser(this, uris)) {
                    Toast.makeText(this, R.string.share_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupSearch() {
        clearBtn.setOnClickListener { editSearch.setText("") }
        editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                queryText = s?.toString()?.trim().orEmpty()
                clearBtn.visibility = if (queryText.isEmpty()) View.GONE else View.VISIBLE
                applyFilter()
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupDateFilter() {
        val openPicker = View.OnClickListener { openDateDialog() }
        dateStartField.setOnClickListener(openPicker)
        dateEndField.setOnClickListener(openPicker)
        resetFilter.setOnClickListener {
            startDate = null
            endDate = null
            updateDateFields()
            applyFilter()
        }
        goShootBtn.setOnClickListener { finish() }
    }

    private fun ensurePermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadPhotos()
        } else {
            permissionLauncher.launch(arrayOf(permission))
        }
    }

    private fun loadPhotos() {
        io.execute {
            val result = try {
                repository.loadAll()
            } catch (e: Exception) {
                emptyList<WorkPhoto>()
            }
            mainHandler.post {
                photos.clear()
                photos.addAll(result)
                buildLocationChips()
                applyFilter()
            }
        }
    }

    private fun applyFilter() {
        val zone = ZoneId.systemDefault()
        val start = startDate
        val end = endDate
        val query = queryText

        val filtered = photos.filter { photo ->
            val day = Instant.ofEpochMilli(photo.takenAt).atZone(zone).toLocalDate()
            val dateOk = (start == null || !day.isBefore(start)) && (end == null || !day.isAfter(end))
            val textOk = query.isEmpty() || photo.location.contains(query, ignoreCase = true)
            dateOk && textOk
        }

        adapter.submit(filtered)
        countText.text = "${filtered.size} 张"

        if (selectionMode) {
            val names = filtered.map { it.name }.toSet()
            val before = selected.size
            selected.retainAll(names)
            if (selected.isEmpty()) {
                exitSelection()
            } else if (before != selected.size) {
                updateSelectionUi()
            }
        }

        val isEmpty = filtered.isEmpty()
        emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        goShootBtn.visibility = if (photos.isEmpty()) View.VISIBLE else View.GONE
        if (isEmpty) {
            emptyDesc.text = if (photos.isEmpty()) {
                getString(R.string.empty_desc)
            } else {
                "没有符合筛选条件的照片\n试试调整日期范围或地点关键词"
            }
        }
    }

    private fun buildLocationChips() {
        chipsContainer.removeAllViews()
        val counts = HashMap<String, Int>()
        for (photo in photos) {
            val location = photo.location.trim()
            if (location.isNotEmpty()) {
                counts[location] = (counts[location] ?: 0) + 1
            }
        }
        if (counts.isEmpty()) {
            chipsScroll.visibility = View.GONE
            return
        }
        chipsScroll.visibility = View.VISIBLE
        val sorted = counts.entries.sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }
        )
        for (entry in sorted.take(10)) {
            val chip = TextView(this).apply {
                text = entry.key
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@GalleryActivity, R.color.ink_sub))
                background = ContextCompat.getDrawable(this@GalleryActivity, R.drawable.bg_chip)
                setPadding(dp(11f), dp(6f), dp(11f), dp(6f))
                maxWidth = dp(210f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                isClickable = true
                setOnClickListener {
                    editSearch.setText(entry.key)
                    editSearch.setSelection(editSearch.text.length)
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = dp(8f)
            chipsContainer.addView(chip, lp)
        }
    }

    private fun openDateDialog() {
        DateFilterDialog(this, startDate, endDate) { start, end ->
            startDate = start
            endDate = end
            updateDateFields()
            applyFilter()
        }.show()
    }

    private fun updateDateFields() {
        val start = startDate
        val end = endDate
        val ink = ContextCompat.getColor(this, R.color.ink)
        val faint = ContextCompat.getColor(this, R.color.ink_faint)

        if (start == null) {
            dateStartField.text = getString(R.string.date_start)
            dateStartField.setTextColor(faint)
            dateStartField.setBackgroundResource(R.drawable.bg_date_field)
        } else {
            dateStartField.text = start.format(dateFormat)
            dateStartField.setTextColor(ink)
            dateStartField.setBackgroundResource(R.drawable.bg_date_field_active)
        }

        if (end == null) {
            dateEndField.text = getString(R.string.date_end)
            dateEndField.setTextColor(faint)
            dateEndField.setBackgroundResource(R.drawable.bg_date_field)
        } else {
            dateEndField.text = end.format(dateFormat)
            dateEndField.setTextColor(ink)
            dateEndField.setBackgroundResource(R.drawable.bg_date_field_active)
        }

        resetFilter.visibility = if (start != null || end != null) View.VISIBLE else View.GONE
    }

    private fun openPhoto(photo: WorkPhoto) {
        PhotoViewerDialog(this, photo, loader, ::deletePhoto, ::sharePhoto).show()
    }

    private fun deletePhoto(photo: WorkPhoto) {
        io.execute {
            val deleted = repository.delete(photo)
            mainHandler.post {
                if (deleted) {
                    loadPhotos()
                } else {
                    Toast.makeText(this, "删除失败，请稍后重试", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (firstResume) {
            firstResume = false
        } else {
            loadPhotos()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        io.shutdownNow()
        loader.shutdown()
    }
}
