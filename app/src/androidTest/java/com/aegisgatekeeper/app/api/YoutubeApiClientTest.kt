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

class YoutubeApiClientTest {
    private val mockJsonResponse =
        """ 
        {
            "items":[
                {
                    "snippet": {
                        "title": "Test Video Title",
                        "channelTitle": "Test Channel"
                    },
                    "contentDetails": {
                        "duration": "PT15M33S"
                    }
                }
            ]
        }
        """.trimIndent()

    @Test
    fun testGetVideoDetails_SuccessfulResponse_ParsesCorrectly() =
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
            val mockClient = createMockClient(mockEngine)

            // Act: We inject our mock client into a temporary test-only instance.
            val apiClient =
                object : Any() {
                    suspend fun getVideoDetails(videoId: String) =
                        YoutubeApiClient.run {
                            val originalClient = this.javaClass.getDeclaredField("client").apply { isAccessible = true }
                            val originalValue = originalClient.get(this)
                            originalClient.set(this, mockClient) // Overwrite client with mock
                            val result = getVideoDetails(videoId) // Run the real function
                            originalClient.set(this, originalValue) // Restore original client
                            result
                        }
                }

            val result = apiClient.getVideoDetails("testVideoId123")

            // Assert
            assertThat(result.isRight()).isTrue()
            val response = result.getOrNull()!!
            assertThat(response.items).hasSize(1)
            assertThat(
                response.items
                    .first()
                    .snippet?.title,
            ).isEqualTo("Test Video Title")
            assertThat(
                response.items
                    .first()
                    .contentDetails?.duration,
            ).isEqualTo("PT15M33S")
        }

    @Test
    fun testGetVideoDetails_ApiError_ReturnsFailure() =
        runTest {
            // Arrange
            val mockEngine =
                MockEngine {
                    respond(
                        content = ByteReadChannel("""{ "error": "API limit exceeded" }"""),
                        status = HttpStatusCode.Forbidden,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val mockClient = createMockClient(mockEngine)

            // Act
            val originalClient = YoutubeApiClient.client
            YoutubeApiClient.client = mockClient
            val result = YoutubeApiClient.getVideoDetails("testVideoId123")
            YoutubeApiClient.client = originalClient

            // Assert
            assertThat(result.isLeft()).isTrue()
        }

    private fun createMockClient(engine: MockEngine): HttpClient =
        HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
}
