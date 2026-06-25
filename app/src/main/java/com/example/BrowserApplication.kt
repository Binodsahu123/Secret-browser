package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import java.io.File
import java.util.Date

class BrowserApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize DevTools ConsoleEngine persistence
        try {
            com.example.browser.OrionDeveloperEngine.initFromPrefs(this)
            com.example.developertoolsengine.ConsoleEngine.instance.initializePersistence(filesDir)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Start background notification engine updater services
        try {
            com.example.notificationengine.BackgroundNotificationService.startEngine(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            throwable.printStackTrace()
            saveErrorLog(throwable)
            
            try {
                val intent = Intent(applicationContext, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    private fun saveErrorLog(throwable: Throwable) {
        try {
            val file = File(filesDir, "crash_log.txt")
            file.writeText("${Date()}\n${throwable.stackTraceToString()}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
