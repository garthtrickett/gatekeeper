package com.aegisgatekeeper.app.api

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.aegisgatekeeper.app.di.Singleton
import com.aegisgatekeeper.app.domain.estimateReadTimeSeconds
import com.aegisgatekeeper.app.domain.parseIso8601Duration
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

@Inject
@Singleton
class UrlMetadataClient(
    private val client: HttpClient,
) {
    suspend fun fetchMetadata(
        url: String,
        isSoundCloud: Boolean = false,
        isGeneric: Boolean = false,
    ): Either<String, com.aegisgatekeeper.app.api.ContentMetadata> =
        withContext(Dispatchers.IO) {
            try {
                val response =
                    client.get(url) {
                        header(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36",
                        )
                        header("Accept-Language", "en-US,en;q=0.9")
                    }

                val html = response.bodyAsText()

                var resolvedUrl = url
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

                com.aegisgatekeeper.app.api
                    .ContentMetadata(title, durationSeconds, resolvedUrl)
                    .right()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                (e.message ?: "Failed to fetch metadata").left()
            }
        }
}
