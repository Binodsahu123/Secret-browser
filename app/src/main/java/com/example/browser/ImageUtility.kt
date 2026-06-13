package com.example.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Environment
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

object ImageUtility {
    fun saveTextToImage(context: Context, fileName: String, content: String) {
        try {
            val lines = content.split("\n")
            
            // Setup Paint objects
            val bgPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#0F172A") // Modern Slate Dark background
            }
            
            val titleBgPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#1E293B") // Indigo header background
            }
            
            val lineNumPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#64748B") // Cool Slate Gray
                textSize = 34f
                isAntiAlias = true
                typeface = Typeface.MONOSPACE
            }
            
            val textPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#38BDF8") // Premium code blue/cyan syntax
                textSize = 36f
                isAntiAlias = true
                typeface = Typeface.MONOSPACE
            }
            
            val titlePaint = Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 42f
                isFakeBoldText = true
                isAntiAlias = true
                typeface = Typeface.DEFAULT_BOLD
            }
            
            val metaPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#94A3B8")
                textSize = 28f
                isAntiAlias = true
                typeface = Typeface.DEFAULT
            }
            
            val borderPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#334155")
                strokeWidth = 3f
                style = Paint.Style.STROKE
            }

            // Page dimensions
            val imageWidth = 1440 // High-definition snippet width
            val paddingX = 50f
            val headerHeight = 160f
            val lineSpacing = 50f
            val maxVisibleLines = 350 // Cap to prevent OOM
            
            val linesToDraw = if (lines.size > maxVisibleLines) {
                lines.take(maxVisibleLines) + "...\n[Snippet truncated for image export]"
            } else {
                lines
            }
            
            val imageHeight = (headerHeight + (linesToDraw.size * lineSpacing) + 100f).toInt()
            
            // Create target bitmap
            val bitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            // Draw slate background
            canvas.drawRect(0f, 0f, imageWidth.toFloat(), imageHeight.toFloat(), bgPaint)
            
            // Draw a stylish header bar
            canvas.drawRect(0f, 0f, imageWidth.toFloat(), headerHeight, titleBgPaint)
            canvas.drawLine(0f, headerHeight, imageWidth.toFloat(), headerHeight, borderPaint)
            
            // File branding inside header
            canvas.drawText("📄 $fileName", paddingX, 65f, titlePaint)
            canvas.drawText("Exported Code Snippet | Total Lines: ${lines.size} | Date: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date())}", paddingX, 120f, metaPaint)
            
            // Draw outer border frame
            canvas.drawRect(0f, 0f, imageWidth.toFloat(), imageHeight.toFloat(), borderPaint)
            
            // Draw body lines
            var currentY = headerHeight + 60f
            val lineNumWidth = 120f
            
            for ((index, line) in linesToDraw.withIndex()) {
                val lineLabel = (index + 1).toString().padStart(4, ' ')
                
                // Draw line number background barrier
                canvas.drawText(lineLabel, paddingX, currentY, lineNumPaint)
                
                // Truncate line if it extends beyond content limits
                val maxTxtWidth = imageWidth - lineNumWidth - paddingX * 2
                var textToDraw = line
                val measuredWidth = textPaint.measureText(line)
                if (measuredWidth > maxTxtWidth) {
                    val charsCount = textPaint.breakText(line, true, maxTxtWidth - 40f, null)
                    textToDraw = line.substring(0, charsCount) + "..."
                }
                
                // Draw the actual text line
                canvas.drawText(textToDraw, paddingX + lineNumWidth, currentY, textPaint)
                currentY += lineSpacing
            }
            
            // Save bitmap to file in standard Downloads folder
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val cleanName = fileName.replace("[^a-zA-Z0-9.-]".toRegex(), "_") + "_snippet.png"
            val imageFile = File(downloadsDir, cleanName)
            
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            // Recycle bitmap memory
            bitmap.recycle()
            
            Toast.makeText(context, "Saved code snippet as PNG to Downloads Folder: ${imageFile.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to save image export: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }
}
