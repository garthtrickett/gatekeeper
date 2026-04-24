package com.aegisgatekeeper.app.api

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

class PodcastIndexClientTest {
    private val mockJsonResponse =
        """ 
        {
            "feeds":[
                {
                    "id": 12345,
                    "title": "Test Podcast",
                    "url": "https://example.com/feed.xml",
                    "image": "https://example.com/image.jpg",
                    "author": "Test Author"
                }
            ]
        }
        """.trimIndent()

    @Test
    fun testSearchPodcasts_SuccessfulResponse_ParsesCorrectly() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(mockJsonResponse.toByteArray(Charsets.UTF_8)),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val mockHttpClient =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

            val originalClient = PodcastIndexClient.client
            try {
                PodcastIndexClient.client = mockHttpClient
                val result = PodcastIndexClient.searchPodcasts("test")

                assertThat(result.isRight()).isTrue()
                val feeds = result.getOrNull()!!
                assertThat(feeds).hasSize(1)
                assertThat(feeds.first().title).isEqualTo("Test Podcast")
                assertThat(feeds.first().url).isEqualTo("https://example.com/feed.xml")
            } finally {
                PodcastIndexClient.client = originalClient
            }
        }

    @Test
    fun testSearchPodcasts_ApiError_ReturnsFailure() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(""),
                        status = HttpStatusCode.InternalServerError,
                    )
                }
            val mockHttpClient =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

            val originalClient = PodcastIndexClient.client
            try {
                PodcastIndexClient.client = mockHttpClient
                val result = PodcastIndexClient.searchPodcasts("test")

                assertThat(result.isLeft()).isTrue()
            } finally {
                PodcastIndexClient.client = originalClient
            }
        }

    @Test
    fun testSearchPodcasts_BlankQuery_ReturnsSuccessWithEmptyList() =
        runTest {
            val result = PodcastIndexClient.searchPodcasts("  ")
            assertThat(result.isRight()).isTrue()
            result.fold(
                ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                ifRight = { feeds ->
                    assertThat(feeds).isEmpty()
                },
            )
        }
}
