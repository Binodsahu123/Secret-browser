package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import java.io.File
import java.util.Date

class BrowserApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
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
