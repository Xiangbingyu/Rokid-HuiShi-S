package com.demo.huishi

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.demo.huishi.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    // 视图绑定
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 初始化视图绑定
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 设置拍照按钮的点击事件
        binding.buttonTakePhoto.setOnClickListener {
            Toast.makeText(this, "点击拍照", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, PhotoCaptureActivity::class.java)
            startActivity(intent)
        }
        
        // 设置查看照片按钮的点击事件
        binding.buttonViewPhotos.setOnClickListener {
            Toast.makeText(this, "点击查看照片", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, PhotoListActivity::class.java)
            startActivity(intent)
        }
    }
}