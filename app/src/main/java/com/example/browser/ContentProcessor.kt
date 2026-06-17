package com.example.browser

import kotlin.math.max

object ContentProcessor {
    fun calculateReadingTimeMinutes(context: WebsiteContext): Int {
        val sb = StringBuilder()
        sb.append(context.title)
        context.headings.forEach { sb.append(" ").append(it) }
        context.subheadings.forEach { sb.append(" ").append(it) }
        context.paragraphs.forEach { sb.append(" ").append(it) }
        context.lists.forEach { sb.append(" ").append(it) }
        context.tables.forEach { sb.append(" ").append(it) }
        context.importantTexts.forEach { sb.append(" ").append(it) }
        
        val wordCount = sb.toString().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
        return max((wordCount / 200), 1)
    }

    fun cleanAndTruncate(text: String, maxLength: Int = 8000): String {
        val cleaned = text.replace("\\s+".toRegex(), " ").trim()
        return if (cleaned.length > maxLength) {
            cleaned.substring(0, maxLength) + "... [Truncated for Context]"
        } else {
            cleaned
        }
    }
}
