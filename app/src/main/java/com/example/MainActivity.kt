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

    private val voicePermissionsLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[android.Manifest.permission.RECORD_AUDIO] == true
        val cameraGranted = permissions[android.Manifest.permission.CAMERA] == true
        android.util.Log.i("MainActivity", "Permissions result. RECORD_AUDIO: $recordAudioGranted, CAMERA: $cameraGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = BrowserDatabase.getDatabase(applicationContext)
        val repository = BrowserRepository(db)
        val prefs = PreferenceManager(applicationContext)
        val factory = BrowserViewModelFactory(application, repository, prefs)

        viewModel = ViewModelProvider(this, factory)[BrowserViewModel::class.java]

        viewModel.handleIncomingIntent(intent)

        // Browser Startup: Request necessary permissions (Notifications + Microphone)
        val permissionsToRequest = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionsToRequest.add(android.Manifest.permission.RECORD_AUDIO)
        
        val permissionsArray = permissionsToRequest.toTypedArray()
        val allGranted = permissionsArray.all {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        
        if (!allGranted) {
            voicePermissionsLauncher.launch(permissionsArray)
        }

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

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::viewModel.isInitialized) {
            viewModel.handleIncomingIntent(intent)
        }
    }

    override fun onPause() {
        if (::viewModel.isInitialized) {
            viewModel.captureActiveVideoState()
            viewModel.saveTabsState()
        }
        super.onPause()
    }

    override fun onStop() {
        if (::viewModel.isInitialized) {
            viewModel.saveTabsState()
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.onAppResume()
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
