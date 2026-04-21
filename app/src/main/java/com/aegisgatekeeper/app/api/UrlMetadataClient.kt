package com.aegisgatekeeper.app.api

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.aegisgatekeeper.app.domain.estimateReadTimeSeconds
import com.aegisgatekeeper.app.domain.parseIso8601Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import javax.net.ssl.HttpsURLConnection

sealed interface UrlMetadataError {
    data class NetworkFailure(
        val message: String,
    ) : UrlMetadataError
}

data class ContentMetadata(
    val title: String,
    val durationSeconds: Long? = null,
    val resolvedUrl: String? = null,
)

object UrlMetadataClient {
    suspend fun fetchMetadata(
        url: String,
        isSoundCloud: Boolean = false,
        isGeneric: Boolean = false,
    ): Either<UrlMetadataError, ContentMetadata> =
        withContext(Dispatchers.IO) {
            try {
                var currentUrl = url
                var connection: java.net.HttpURLConnection
                var redirects = 0

                while (true) {
                    connection = URL(currentUrl).openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36",
                    )
                    connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                    connection.instanceFollowRedirects = false

                    val status = connection.responseCode
                    if (status in 300..399) {
                        val location = connection.getHeaderField("Location")
                        if (location != null) {
                            currentUrl =
                                if (location.startsWith("/")) {
                                    URL(URL(currentUrl), location).toString()
                                } else {
                                    location
                                }
                            redirects++
                            if (redirects > 10) break
                            continue
                        }
                    }
                    break
                }

                val html = connection.inputStream.readBytes().toString(Charsets.UTF_8)

                var resolvedUrl = currentUrl
                val canonicalRegex = """<link\s+rel=["']canonical["']\s+href=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
                val canonicalMatch = canonicalRegex.find(html)
                if (canonicalMatch != null) {
                    resolvedUrl = canonicalMatch.groupValues[1]
                } else {
                    val ogUrlRegex = """<meta\s+property=["']og:url["']\s+content=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
                    val ogUrlMatch = ogUrlRegex.find(html)
                    if (ogUrlMatch != null) {
                        resolvedUrl = ogUrlMatch.groupValues[1]
                    }
                }

                val titleRegex = """<title>(.*?)</title>""".toRegex()
                val match = titleRegex.find(html)
                var title = match?.groupValues?.get(1)?.trim() ?: "Unknown Title"
                title = title.replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"")

                var durationSeconds: Long? = null

                if (isSoundCloud) {
                    title = title.replace(" | Listen online for free on SoundCloud", "").trim()
                    val durationRegex = """"duration":(\d+)""".toRegex()
                    val durationMatch = durationRegex.find(html)
                    if (durationMatch != null) {
                        durationSeconds = durationMatch.groupValues[1].toLong() / 1000
                    } else {
                        val metaDurationRegex = """<meta itemprop="duration" content="([^"]+)">""".toRegex()
                        val metaMatch = metaDurationRegex.find(html)
                        if (metaMatch != null) {
                            durationSeconds = parseIso8601Duration(metaMatch.groupValues[1])
                        }
                    }
                } else if (isGeneric) {
                    durationSeconds = estimateReadTimeSeconds(html)
                } else {
                    title = title.replace(" - YouTube", "").trim()
                }

                ContentMetadata(title, durationSeconds, resolvedUrl).right()
            } catch (e: Exception) {
                UrlMetadataError.NetworkFailure(e.message ?: "Failed to fetch metadata").left()
            }
        }
}
