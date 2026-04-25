package com.aegisgatekeeper.app.api

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.aegisgatekeeper.app.domain.parseItunesDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class RssEpisode(
    val title: String,
    val audioUrl: String,
    val durationSeconds: Long?,
    val pubDate: String?,
)

data class RssFeedData(
    val title: String,
    val artworkUrl: String?,
    val episodes: List<RssEpisode>,
)

object RssClient {
    suspend fun fetchFeed(feedUrl: String): Either<String, RssFeedData> =
        withContext(Dispatchers.IO) {
            try {
                var currentUrl = feedUrl
                var connection: HttpURLConnection
                var redirects = 0

                while (true) {
                    connection = URL(currentUrl).openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36",
                    )
                    connection.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*")
                    connection.connectTimeout = 30000
                    connection.readTimeout = 30000
                    connection.instanceFollowRedirects = false

                    val status = connection.responseCode
                    if (status in 300..399) {
                        val location = connection.getHeaderField("Location")
                        if (location != null) {
                            currentUrl = location
                            redirects++
                            if (redirects > 10) break
                            continue
                        }
                    }
                    break
                }

                if (connection.responseCode !in 200..299) {
                    return@withContext "HTTP Error: ${connection.responseCode}".left()
                }

                val xml = connection.inputStream.bufferedReader().use { it.readText() }

                // Basic parsing using highly predictable regex for standard RSS structure
                val channelTitle =
                    Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
                        .find(xml)
                        ?.groupValues
                        ?.get(1)
                        ?.replace("<!\\[CDATA\\[".toRegex(), "")
                        ?.replace("]]>".toRegex(), "")
                        ?.trim() ?: "Unknown Podcast"

                val imageMatch =
                    Regex("<image>.*?<url>(.*?)</url>.*?</image>", RegexOption.DOT_MATCHES_ALL).find(xml)
                        ?: Regex("<itunes:image href=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(xml)
                val artworkUrl = imageMatch?.groupValues?.get(1)

                val items = Regex("<item[^>]*>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
                val episodes =
                    items
                        .mapNotNull { match ->
                            val itemXml = match.groupValues[1]
                            val title =
                                Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
                                    .find(itemXml)
                                    ?.groupValues
                                    ?.get(1)
                                    ?.replace("<!\\[CDATA\\[".toRegex(), "")
                                    ?.replace("]]>".toRegex(), "")
                                    ?.trim()

                            val enclosure =
                                Regex(
                                    "<enclosure[^>]*?url=[\"']([^\"']+)[\"']",
                                    RegexOption.IGNORE_CASE,
                                ).find(itemXml)?.groupValues?.get(1)
                            val durationStr =
                                Regex(
                                    "<itunes:duration[^>]*>(.*?)</itunes:duration>",
                                    RegexOption.IGNORE_CASE,
                                ).find(itemXml)?.groupValues?.get(1)
                            val pubDate = Regex("<pubDate[^>]*>(.*?)</pubDate>", RegexOption.IGNORE_CASE).find(itemXml)?.groupValues?.get(1)
                                ?: Regex("<published[^>]*>(.*?)</published>", RegexOption.IGNORE_CASE).find(itemXml)?.groupValues?.get(1)
                                ?: Regex("<dc:date[^>]*>(.*?)</dc:date>", RegexOption.IGNORE_CASE).find(itemXml)?.groupValues?.get(1)

                            if (title != null && enclosure != null) {
                                val duration = durationStr?.let { parseItunesDuration(it) }
                                RssEpisode(title, enclosure, duration, pubDate)
                            } else {
                                null
                            }
                        }.toList()

                if (episodes.isEmpty()) {
                    return@withContext "No episodes found in feed".left()
                }

                RssFeedData(channelTitle, artworkUrl, episodes).right()
            } catch (e: Exception) {
                android.util.Log.e("Gatekeeper", "❌ RssClient Exception", e)
                (e.message ?: "Unknown error").left()
            }
        }
}
