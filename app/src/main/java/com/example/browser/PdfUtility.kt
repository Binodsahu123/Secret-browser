package com.example.browser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.widget.Toast
import java.io.File

object PdfUtility {
    fun printWebView(context: Context, webView: WebView, title: String = "WebPage") {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val jobName = "${title.replace("[^a-zA-Z0-9]".toRegex(), "_")}_Document"
            val printAdapter = webView.createPrintDocumentAdapter(jobName)
            
            printManager.print(
                jobName,
                printAdapter,
                PrintAttributes.Builder().build()
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Error initiating PDF export: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun printTextToPdf(context: Context, fileName: String, content: String) {
        try {
            val pdfDocument = PdfDocument()
            val textPaint = android.text.TextPaint().apply {
                color = android.graphics.Color.BLACK
                textSize = 10f
                isAntiAlias = true
                typeface = android.graphics.Typeface.MONOSPACE
            }
            
            val lineNumPaint = android.text.TextPaint().apply {
                color = android.graphics.Color.GRAY
                textSize = 9f
                isAntiAlias = true
                typeface = android.graphics.Typeface.MONOSPACE
            }

            val titlePaint = Paint().apply {
                color = android.graphics.Color.parseColor("#4F46E5") // Indigo dye
                textSize = 15f
                isFakeBoldText = true
                isAntiAlias = true
            }

            val metaPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#4B5563")
                textSize = 8f
                isAntiAlias = true
            }

            val linePaint = Paint().apply {
                color = android.graphics.Color.parseColor("#E5E7EB")
                strokeWidth = 1f
            }

            val lines = content.split("\n")
            val marginX = 40f
            val marginY = 50f
            val pageWidth = 595 // A4 standard width in points
            val pageHeight = 842 // A4 standard height in points
            val contentWidth = pageWidth - (marginX * 2)
            val contentHeight = pageHeight - (marginY * 2) - 40f
            val lineSpacing = 13f
            val maxLinesPerPage = (contentHeight / lineSpacing).toInt()

            var pageNumber = 1
            var lineIndex = 0

            while (lineIndex < lines.size) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // Header Block for first page
                if (pageNumber == 1) {
                    canvas.drawText(fileName, marginX, marginY, titlePaint)
                    canvas.drawText("Format: Text/Code Source Document | Date: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date())}", marginX, marginY + 15f, metaPaint)
                    canvas.drawLine(marginX, marginY + 25f, pageWidth - marginX, marginY + 25f, linePaint)
                }

                var currentY = if (pageNumber == 1) marginY + 45f else marginY
                var linesOnPage = 0

                while (lineIndex < lines.size && linesOnPage < maxLinesPerPage) {
                    val rawLine = lines[lineIndex]
                    val lineNumText = (lineIndex + 1).toString().padStart(4, ' ') + " | "
                    
                    // Measure line number prefix width
                    val prefixWidth = lineNumPaint.measureText(lineNumText)
                    val maxTextWidth = contentWidth - prefixWidth

                    // Draw line number
                    canvas.drawText(lineNumText, marginX, currentY, lineNumPaint)

                    // Wrap line if it's too wide for the page
                    if (textPaint.measureText(rawLine) <= maxTextWidth) {
                        canvas.drawText(rawLine, marginX + prefixWidth, currentY, textPaint)
                        currentY += lineSpacing
                        linesOnPage++
                    } else {
                        // Split line into wrapped chunks
                        var remaining = rawLine
                        var firstChunk = true
                        while (remaining.isNotEmpty() && linesOnPage < maxLinesPerPage) {
                            var count = textPaint.breakText(remaining, true, maxTextWidth, null)
                            if (count <= 0) break
                            
                            val chunk = remaining.substring(0, count)
                            remaining = remaining.substring(count)

                            val drawX = if (firstChunk) marginX + prefixWidth else marginX + prefixWidth + 15f
                            canvas.drawText(chunk, drawX, currentY, textPaint)
                            currentY += lineSpacing
                            linesOnPage++
                            firstChunk = false
                        }
                    }

                    lineIndex++
                }

                // Footer
                val footerText = "Page $pageNumber"
                val footerX = (pageWidth - textPaint.measureText(footerText)) / 2f
                canvas.drawText(footerText, footerX, pageHeight - 20f, lineNumPaint)

                pdfDocument.finishPage(page)
                pageNumber++
            }

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val cleanName = fileName.replace("[^a-zA-Z0-9.-]".toRegex(), "_") + ".pdf"
            val pdfFile = File(downloadsDir, cleanName)
            pdfDocument.writeTo(pdfFile.outputStream())
            pdfDocument.close()

            Toast.makeText(context, "Saved code file as PDF code-doc to Downloads Folder: ${pdfFile.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error converting code to PDF: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }
}
