package com.aegisgatekeeper.app.api

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.aegisgatekeeper.app.di.Singleton
import com.aegisgatekeeper.app.domain.parseItunesDuration
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import java.net.HttpURLConnection
import java.net.URL

class RssFeedData_Deleted {}

@Inject
@Singleton
class RssClient(
    private val client: HttpClient,
) {
    suspend fun fetchFeed(feedUrl: String): Either<String, RssFeedData> =
        withContext(Dispatchers.IO) {
            try {
                val response =
                    client.get(feedUrl) {
                        header(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36",
                        )
                        header("Accept", "application/rss+xml, application/xml, text/xml, */*")
                    }
                if (response.status.value !in 200..299) {
                    return@withContext "HTTP Error: ${response.status.value}".left()
                }

                val xml = response.bodyAsText()

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
                            val pubDate =
                                Regex("<pubDate[^>]*>(.*?)</pubDate>", RegexOption.IGNORE_CASE).find(itemXml)?.groupValues?.get(1)
                                    ?: Regex(
                                        "<published[^>]*>(.*?)</published>",
                                        RegexOption.IGNORE_CASE,
                                    ).find(itemXml)?.groupValues?.get(1)
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
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("Gatekeeper", "❌ RssClient Exception", e)
                (e.message ?: "Unknown error").left()
            }
        }
}
