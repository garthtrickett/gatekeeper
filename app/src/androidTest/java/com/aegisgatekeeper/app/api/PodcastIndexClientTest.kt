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
            "results":[
                {
                    "collectionId": 12345,
                    "collectionName": "Test Podcast",
                    "feedUrl": "https://example.com/feed.xml",
                    "artworkUrl600": "https://example.com/image.jpg",
                    "artistName": "Test Author"
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

            val client = PodcastIndexClient(mockHttpClient)
            val result = client.searchPodcasts("test")

            assertThat(result.isRight()).isTrue()
            val feeds = result.getOrNull()!!
            assertThat(feeds).hasSize(1)
            assertThat(feeds.first().title).isEqualTo("Test Podcast")
            assertThat(feeds.first().url).isEqualTo("https://example.com/feed.xml")
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

            val client = PodcastIndexClient(mockHttpClient)
            val result = client.searchPodcasts("test")

            assertThat(result.isLeft()).isTrue()
        }

    @Test
    fun testSearchPodcasts_BlankQuery_ReturnsSuccessWithEmptyList() =
        runTest {
            // The client is no longer a singleton, so we need to instantiate it
            val mockHttpClient = HttpClient(MockEngine { respond("") })
            val client = PodcastIndexClient(mockHttpClient)
            val result = client.searchPodcasts("  ")
            assertThat(result.isRight()).isTrue()
            result.fold(
                ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                ifRight = { feeds ->
                    assertThat(feeds).isEmpty()
                },
            )
        }
}
