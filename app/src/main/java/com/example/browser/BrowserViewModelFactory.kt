package com.example.browser

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.BrowserRepository
import com.example.data.PreferenceManager

class BrowserViewModelFactory(
    private val application: Application,
    private val repository: BrowserRepository,
    private val prefs: PreferenceManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BrowserViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BrowserViewModel(application, repository, prefs) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
