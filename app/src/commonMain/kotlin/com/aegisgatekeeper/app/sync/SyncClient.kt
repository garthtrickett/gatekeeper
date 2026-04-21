package com.aegisgatekeeper.app.sync

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.aegisgatekeeper.app.auth.TokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Cross-platform synchronization client.
 * Uses Ktor to communicate with the Sovereign Sync Backend.
 */
object SyncClient {
    private val client =
        HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

    suspend fun registerDevice(token: String): Either<SyncError, Unit> {
        val jwtToken = TokenProvider.getToken() ?: return SyncError.Unauthorized.left()
        val baseUrl = TokenProvider.getSyncServerUrl().trimEnd('/')

        return try {
            val response =
                client.post("$baseUrl/sync/register-device") {
                    header("Authorization", "Bearer $jwtToken")
                    contentType(ContentType.Application.Json)
                    setBody(
                        com.aegisgatekeeper.app.sync
                            .DeviceRegistrationRequest(token),
                    )
                }
            if (response.status.value in 200..299) {
                Unit.right()
            } else if (response.status.value == 401 || response.status.value == 403) {
                SyncError.Unauthorized.left()
            } else {
                SyncError.ServerError(response.status.value).left()
            }
        } catch (e: Exception) {
            SyncError.NetworkFailure(e.message ?: "Unknown network failure").left()
        }
    }

    suspend fun pushChanges(payload: SyncPushPayload): Either<SyncError, Unit> {
        val token = TokenProvider.getToken() ?: return SyncError.Unauthorized.left()
        val baseUrl = TokenProvider.getSyncServerUrl().trimEnd('/')

        return try {
            val response =
                client.post("$baseUrl/sync/push") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
            if (response.status.value in 200..299) {
                Unit.right()
            } else if (response.status.value == 401 || response.status.value == 403) {
                SyncError.Unauthorized.left()
            } else {
                SyncError.ServerError(response.status.value).left()
            }
        } catch (e: Exception) {
            SyncError.NetworkFailure(e.message ?: "Unknown network failure").left()
        }
    }

    suspend fun pullChanges(lastSyncTimestamp: Long = 0L): Either<SyncError, SyncPullPayload> {
        val token = TokenProvider.getToken() ?: return SyncError.Unauthorized.left()
        val baseUrl = TokenProvider.getSyncServerUrl().trimEnd('/')

        return try {
            val response =
                client
                    .get("$baseUrl/sync/pull?since=$lastSyncTimestamp") {
                        header("Authorization", "Bearer $token")
                    }
            if (response.status.value in 200..299) {
                response.body<SyncPullPayload>().right()
            } else if (response.status.value == 401 || response.status.value == 403) {
                SyncError.Unauthorized.left()
            } else {
                SyncError.ServerError(response.status.value).left()
            }
        } catch (e: Exception) {
            SyncError.NetworkFailure(e.message ?: "Unknown network failure").left()
        }
    }
}
