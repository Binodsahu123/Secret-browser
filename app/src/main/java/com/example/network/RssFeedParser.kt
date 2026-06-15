package com.example.network

import android.util.Xml
import com.example.data.ArticleCacheEntity
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

object RssFeedParser {
    fun fetchAndParseRss(
        client: OkHttpClient,
        url: String,
        category: String
    ): List<ArticleCacheEntity> {
        val resultList = mutableListOf<ArticleCacheEntity>()
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val bodyStr = response.body?.string() ?: return emptyList()

                val parser = Xml.newPullParser()
                parser.setInput(StringReader(bodyStr))
                var eventType = parser.eventType

                var currentTitle = ""
                var currentLink = ""
                var currentDescription = ""
                var currentPubDate = ""
                var currentImgUrl: String? = null

                var insideItem = false

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val tagName = parser.name
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            if (tagName.equals("item", ignoreCase = true)) {
                                insideItem = true
                                currentTitle = ""
                                currentLink = ""
                                currentDescription = ""
                                currentPubDate = ""
                                currentImgUrl = null
                            } else if (insideItem) {
                                when {
                                    tagName.equals("title", ignoreCase = true) -> {
                                        currentTitle = parser.nextText().trim()
                                    }
                                    tagName.equals("link", ignoreCase = true) -> {
                                        currentLink = parser.nextText().trim()
                                    }
                                    tagName.equals("description", ignoreCase = true) -> {
                                        currentDescription = parser.nextText().trim().replace("<[^>]*>".toRegex(), " ").trim()
                                    }
                                    tagName.equals("pubDate", ignoreCase = true) -> {
                                        currentPubDate = parser.nextText().trim()
                                    }
                                    tagName.equals("media:content", ignoreCase = true) || tagName.equals("enclosure", ignoreCase = true) -> {
                                        val urlAttr = parser.getAttributeValue(null, "url")
                                        if (!urlAttr.isNullOrBlank()) {
                                            currentImgUrl = urlAttr
                                        }
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (tagName.equals("item", ignoreCase = true)) {
                                insideItem = false
                                if (currentLink.isNotBlank() && currentTitle.isNotBlank()) {
                                    resultList.add(
                                        ArticleCacheEntity(
                                            url = currentLink,
                                            title = currentTitle,
                                            description = currentDescription,
                                            imageUrl = currentImgUrl,
                                            category = category,
                                            publishedAt = currentPubDate,
                                            source = getDomain(currentLink)
                                        )
                                    )
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return resultList
    }

    private fun getDomain(urlStr: String): String {
        return try {
            val uri = java.net.URI(urlStr)
            var domain = uri.host ?: ""
            if (domain.startsWith("www.")) {
                domain = domain.substring(4)
            }
            domain
        } catch (e: Exception) {
            "Unknown Source"
        }
    }
}
