package com.demo.huishi

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PhotoCaptureActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "PhotoCaptureActivity"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    // UI控件
    private lateinit var cameraPreview: PreviewView
    private lateinit var capturedImage: ImageView
    private lateinit var captureButton: Button
    private lateinit var captureHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_capture)

        // 保持屏幕常亮
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 初始化UI控件
        cameraPreview = findViewById(R.id.camera_preview)
        capturedImage = findViewById(R.id.captured_image)
        captureButton = findViewById(R.id.capture_button)
        captureHint = findViewById(R.id.capture_hint)

        // 请求相机权限
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions.launch(REQUIRED_PERMISSIONS)
        }

        // 设置拍照按钮点击事件
        captureButton.setOnClickListener {
            takePhoto()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    // 权限请求
    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var permissionGranted = true
        permissions.entries.forEach { entry ->
            if (entry.key in REQUIRED_PERMISSIONS && entry.value == false) {
                permissionGranted = false
            }
        }

        if (permissionGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "相机权限被拒绝", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // 检查是否所有权限都已授予
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    // 启动相机
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // 配置预览
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(cameraPreview.surfaceProvider)
                }

            // 配置图像捕获
            imageCapture = ImageCapture.Builder()
                .build()

            // 选择相机（后置）
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // 解绑之前的相机使用情况
                cameraProvider.unbindAll()

                // 绑定相机到生命周期
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )

            } catch (exc: Exception) {
                Log.e(TAG, "使用相机时发生错误: ${exc.message}", exc)
                Toast.makeText(this, "相机启动失败", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // 拍照功能
    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        // 创建保存图片的文件名
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.CHINA)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
            }
        }

        // 配置图片输出选项
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()

        // 执行拍照
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "拍照失败: ${exc.message}", exc)
                    Toast.makeText(this@PhotoCaptureActivity, "拍照失败", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri ?: return
                    
                    Log.d(TAG, "照片已保存: $savedUri")
                    
                    // 显示拍摄的照片
                    showCapturedImage(savedUri)
                    
                    Toast.makeText(
                        this@PhotoCaptureActivity,
                        "照片已保存到图库",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    // 显示拍摄的照片
    private fun showCapturedImage(uri: Uri) {
        cameraPreview.visibility = View.GONE
        capturedImage.visibility = View.VISIBLE
        captureHint.text = "拍摄完成！"
        
        lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            } catch (e: IOException) {
                Log.e(TAG, "加载图片失败: ${e.message}", e)
                null
            }
            
            withContext(Dispatchers.Main) {
                if (bitmap != null) {
                    capturedImage.setImageBitmap(bitmap)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
