package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.browser.BrowserScreen
import com.example.browser.BrowserViewModel
import com.example.browser.BrowserViewModelFactory
import com.example.data.BrowserDatabase
import com.example.data.BrowserRepository
import com.example.data.PreferenceManager
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: BrowserViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = BrowserDatabase.getDatabase(applicationContext)
        val repository = BrowserRepository(db)
        val prefs = PreferenceManager(applicationContext)
        val factory = BrowserViewModelFactory(application, repository, prefs)

        viewModel = ViewModelProvider(this, factory)[BrowserViewModel::class.java]

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BrowserScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (::viewModel.isInitialized) {
            viewModel.clearWebViewCache(applicationContext)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE && ::viewModel.isInitialized) {
            viewModel.clearWebViewCache(applicationContext)
        }
    }
}
