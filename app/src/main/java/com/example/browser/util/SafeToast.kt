package com.example.browser.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

object SafeToast {
    private var currentToast: Toast? = null

    fun showShort(context: Context, text: CharSequence) {
        show(context, text, Toast.LENGTH_SHORT)
    }

    fun showLong(context: Context, text: CharSequence) {
        show(context, text, Toast.LENGTH_LONG)
    }

    @Synchronized
    fun show(context: Context, text: CharSequence, duration: Int) {
        val appContext = context.applicationContext
        val runnable = Runnable {
            try {
                currentToast?.cancel()
                val toast = Toast.makeText(appContext, text, duration)
                currentToast = toast
                toast.show()
            } catch (e: Exception) {
                // Ignore potential exceptions from background threads / context
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run()
        } else {
            Handler(Looper.getMainLooper()).post(runnable)
        }
    }
}
