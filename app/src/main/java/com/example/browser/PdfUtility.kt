package com.example.browser

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView

object PdfUtility {
    fun printWebView(context: Context, webView: WebView, title: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val jobName = "${title.replace("[^a-zA-Z0-9]".toRegex(), "_")}_WebPage"
        val printAdapter = webView.createPrintDocumentAdapter(jobName)
        printManager.print(
            jobName,
            printAdapter,
            PrintAttributes.Builder().build()
        )
    }
}
