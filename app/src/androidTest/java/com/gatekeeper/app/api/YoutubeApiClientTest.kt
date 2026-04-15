package com.gatekeeper.app.api

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

class YoutubeApiClientTest {
    private val mockJsonResponse =
        """ 
        {
            "items": [
                {
                    "id": {
                        "videoId": "testVideoId123"
                    },
                    "snippet": {
                        "title": "Test Video Title",
                        "channelTitle": "Test Channel",
                        "thumbnails": {
                            "high": {
                                "url": "https://example.com/thumbnail.jpg"
                            }
                        }
                    }
                }
            ]
        }
        """.trimIndent()

    @Test
    fun testSearchVideos_SuccessfulResponse_ParsesCorrectly() =
        runTest {
            // Arrange
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(mockJsonResponse.toByteArray(Charsets.UTF_8)),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = createMockClient(mockEngine)

            // Act: We inject our mock client into a temporary test-only instance.
            val apiClient =
                object : Any() {
                    suspend fun searchVideos(query: String) =
                        YoutubeApiClient.run {
                            val originalClient = this.javaClass.getDeclaredField("client").apply { isAccessible = true }
                            val originalValue = originalClient.get(this)
                            originalClient.set(this, client) // Overwrite client with mock
                            val result = searchVideos(query) // Run the real function
                            originalClient.set(this, originalValue) // Restore original client
                            result
                        }
                }

            val result = apiClient.searchVideos("kotlin")

            // Assert
            assertThat(result.isSuccess).isTrue()
            val response = result.getOrThrow()
            assertThat(response.items).hasSize(1)
            assertThat(
                response.items
                    .first()
                    .id.videoId,
            ).isEqualTo("testVideoId123")
            assertThat(
                response.items
                    .first()
                    .snippet.title,
            ).isEqualTo("Test Video Title")
        }

    @Test
    fun testSearchVideos_ApiError_ReturnsFailure() =
        runTest {
            // Arrange
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel("{ 'error': 'API limit exceeded' }"),
                        status = HttpStatusCode.Forbidden,
                    )
                }
            val client = createMockClient(mockEngine)

            // Act
            val apiClient =
                object : Any() {
                    suspend fun searchVideos(query: String) =
                        YoutubeApiClient.run {
                            val originalClient = this.javaClass.getDeclaredField("client").apply { isAccessible = true }
                            val originalValue = originalClient.get(this)
                            originalClient.set(this, client)
                            val result = searchVideos(query)
                            originalClient.set(this, originalValue)
                            result
                        }
                }

            val result = apiClient.searchVideos("kotlin")

            // Assert
            assertThat(result.isFailure).isTrue()
        }

    @Test
    fun testSearchVideos_BlankQuery_ReturnsSuccessWithEmptyList() =
        runTest {
            val result = YoutubeApiClient.searchVideos("  ")
            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()?.items).isEmpty()
        }

    private fun createMockClient(engine: MockEngine): HttpClient =
        HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
}
