package com.demo.huishi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PhotoListActivity : AppCompatActivity() {

    data class MediaItem(
        val uri: Uri,
        val type: MediaType,
        val dateTaken: Long,
    )
    enum class MediaType { IMAGE }
    companion object {
        private const val DEBUG = true
        private const val TAG = "PhotoListActivity"

        private fun debugLog(message: String) { if (DEBUG) Log.d(TAG, message) }
    }

    private lateinit var latestImageView: ImageView
    private lateinit var buttonNext: MaterialButton
    private lateinit var photoCountTextView: TextView

    private var allMediaItems = mutableListOf<MediaItem>()
    private var currentImageIndex = -1

    private lateinit var loadingIndicator: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_photo_list)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initializeViews()
        setupListeners()
        checkAndRequestPermission()
        updatePhotoCountText()
    }

    private fun initializeViews() {
        latestImageView = findViewById(R.id.latestImageView)
        buttonNext = findViewById(R.id.buttonNext)
        photoCountTextView = findViewById(R.id.photoCountTextView)
        loadingIndicator = findViewById(R.id.loadingIndicator)
    }

    private fun setupListeners() {
        buttonNext.setOnClickListener { loadNextMedia() }
    }

    private fun checkAndRequestPermission() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        permissionsToRequest.takeIf { it.isNotEmpty() }?.let {
            requestPermissions.launch(it.toTypedArray())
        } ?: loadAllMediaUris()
    }

    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val hasPermission = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        
        if (hasPermission) loadAllMediaUris() else {
            Toast.makeText(this, "读取权限被拒绝，无法加载照片", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadAllMediaUris() {
        lifecycleScope.launch {
            showLoadingIndicator(true)
            val mediaResult = withContext(Dispatchers.IO) {
                val imageItems = queryMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 
                    Environment.DIRECTORY_DCIM + File.separator + "Camera", MediaType.IMAGE)
                val picturesItems = queryMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 
                    Environment.DIRECTORY_PICTURES, MediaType.IMAGE)
                (imageItems + picturesItems).sortedByDescending { it.dateTaken }
            }
            showLoadingIndicator(false)
            allMediaItems.clear()
            allMediaItems.addAll(mediaResult)
            currentImageIndex = -1

            if (allMediaItems.isEmpty()) {
                Toast.makeText(this@PhotoListActivity, "没有找到照片", Toast.LENGTH_SHORT).show()
            } else {
                debugLog("Total media loaded: ${allMediaItems.size}")
                loadSpecificMedia(0)
                buttonNext.visibility = View.VISIBLE
            }
            updatePhotoCountText()
        }
    }

    private fun queryMedia(
        contentUri: Uri,
        folderPath: String,
        mediaType: MediaType
    ): List<MediaItem> {
        val mediaItems = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATA
        )

        val selection = "${MediaStore.MediaColumns.DATA} LIKE ?"
        val selectionArgs = arrayOf("%${folderPath}%")

        val sortOrder = "${MediaStore.MediaColumns.DATE_TAKEN} DESC"

        contentResolver.query(
            contentUri,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val dateTaken = cursor.getLong(dateTakenColumn)
                val data = cursor.getString(dataColumn)

                val uri = Uri.withAppendedPath(
                    contentUri,
                    id.toString()
                )

                mediaItems.add(MediaItem(uri, mediaType, dateTaken))
                debugLog("Found media: $uri, date: $dateTaken, path: $data")
            }
        }
        return mediaItems
    }

    private fun loadSpecificMedia(index: Int) {
        if (index !in allMediaItems.indices) {
            handleNoPhotosFound()
            return
        }
        currentImageIndex = index
        val item = allMediaItems[index]
        debugLog("Displaying ${item.type.name} → ${item.uri}")

        latestImageView.visibility = View.VISIBLE

        Glide.with(this)
            .load(item.uri)
            .into(latestImageView)
        debugLog("Displaying image.")
        updatePhotoCountText()
    }

    private fun loadNextMedia() {
        if (allMediaItems.isEmpty()) {
            handleNoPhotosFound()
            return
        }
        currentImageIndex = (currentImageIndex + 1) % allMediaItems.size
        loadSpecificMedia(currentImageIndex)
    }

    private fun updatePhotoCountText() {
        if (allMediaItems.isEmpty()) {
            photoCountTextView.text = "没有照片"
        } else {
            photoCountTextView.text = "照片 ${currentImageIndex + 1} / ${allMediaItems.size}"
        }
    }

    private fun handleNoPhotosFound() {
        latestImageView.visibility = View.INVISIBLE
        buttonNext.visibility = View.INVISIBLE
        updatePhotoCountText()
    }

    private fun showLoadingIndicator(show: Boolean) {
        loadingIndicator.visibility = if (show) View.VISIBLE else View.GONE
    }
}