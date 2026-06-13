package com.example.imageengine

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class ImageViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val filePath = intent.getStringExtra("file_path") ?: ""
        val originalUrl = intent.getStringExtra("original_url") ?: ""

        if (filePath.isEmpty()) {
            Toast.makeText(this, "No image specified", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            ImageViewerComponent(
                filePath = filePath,
                originalUrl = originalUrl,
                onDismiss = { finish() }
            )
        }
    }
}
