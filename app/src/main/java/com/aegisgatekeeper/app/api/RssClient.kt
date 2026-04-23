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
                val connection = URL(feedUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "Gatekeeper/1.0")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val xml = connection.inputStream.bufferedReader().use { it.readText() }

                // Basic parsing using highly predictable regex for standard RSS structure
                val channelTitle =
                    Regex(
                        "<title>(.*?)</title>",
                    ).find(xml)?.groupValues?.get(1)?.replace("<!\\[CDATA\\[".toRegex(), "")?.replace("]]>".toRegex(), "")?.trim()
                        ?: "Unknown Podcast"
                val imageMatch =
                    Regex("<image>.*?<url>(.*?)</url>.*?</image>", RegexOption.DOT_MATCHES_ALL).find(xml)
                        ?: Regex("<itunes:image href=\"(.*?)\"").find(xml)
                val artworkUrl = imageMatch?.groupValues?.get(1)

                val items = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
                val episodes =
                    items
                        .mapNotNull { match ->
                            val itemXml = match.value
                            val title =
                                Regex(
                                    "<title>(.*?)</title>",
                                ).find(
                                    itemXml,
                                )?.groupValues
                                    ?.get(1)
                                    ?.replace("<!\\[CDATA\\[".toRegex(), "")
                                    ?.replace("]]>".toRegex(), "")
                                    ?.trim()
                            val enclosure = Regex("<enclosure.*?url=\"(.*?)\"").find(itemXml)?.groupValues?.get(1)
                            val durationStr = Regex("<itunes:duration>(.*?)</itunes:duration>").find(itemXml)?.groupValues?.get(1)
                            val pubDate = Regex("<pubDate>(.*?)</pubDate>").find(itemXml)?.groupValues?.get(1)

                            if (title != null && enclosure != null) {
                                val duration = durationStr?.let { parseItunesDuration(it) }
                                RssEpisode(title, enclosure, duration, pubDate)
                            } else {
                                null
                            }
                        }.toList()

                RssFeedData(channelTitle, artworkUrl, episodes).right()
            } catch (e: Exception) {
                (e.message ?: "Unknown error").left()
            }
        }
}
