package com.aegisgatekeeper.app.sync

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.aegisgatekeeper.app.di.GlobalDI
import com.aegisgatekeeper.app.domain.readResource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Cross-platform synchronization client.
 * Uses Ktor to communicate with the Sovereign Sync Backend.
 */
data class FilterRulesResult(
    val rules: List<String>,
    val hash: String,
)

object SyncClient {
    private val client =
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

    suspend fun registerDevice(token: String): Either<SyncError, Unit> {
        val jwtToken =
            GlobalDI.component.tokenProvider
                .getToken() ?: return SyncError.Unauthorized.left()
        val baseUrl =
            GlobalDI.component.tokenProvider
                .getSyncServerUrl()
                .trimEnd('/')

        return try {
            val response =
                client.post("$baseUrl/sync/register-device") {
                    header("Authorization", "Bearer $jwtToken")
                    contentType(ContentType.Application.Json)
                    setBody(
                        DeviceRegistrationRequest(token),
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
            if (e is kotlinx.coroutines.CancellationException) throw e
            SyncError.NetworkFailure(e.message ?: "Unknown network failure").left()
        }
    }

    suspend fun pushChanges(payload: SyncPushPayload): Either<SyncError, Unit> {
        val token =
            GlobalDI.component.tokenProvider
                .getToken() ?: return SyncError.Unauthorized.left()
        val baseUrl =
            GlobalDI.component.tokenProvider
                .getSyncServerUrl()
                .trimEnd('/')

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
            if (e is kotlinx.coroutines.CancellationException) throw e
            SyncError.NetworkFailure(e.message ?: "Unknown network failure").left()
        }
    }

    private fun loadBundledFilterRules(): Either<SyncError, FilterRulesResult> {
        com.aegisgatekeeper.app.domain
            .platformLog("Gatekeeper", "⬇️ SyncClient: Network failed, falling back to bundled filter lists.")
        val filterPaths =
            listOf(
                "filters/adguard_mobile.txt",
                "filters/ublock_privacy.txt",
            )
        val rawLists =
            filterPaths.mapNotNull {
                readResource(it)
            }
        return if (rawLists.isEmpty()) {
            SyncError.NetworkFailure("Failed to load any bundled filter lists.").left()
        } else {
            com.aegisgatekeeper.app.domain.platformLog("Gatekeeper", "✅ SyncClient: Loaded ${rawLists.size} filter lists from local resources.")
            parseFilterRules(rawLists).right()
        }
    }

    suspend fun fetchFilterRules(): Either<SyncError, FilterRulesResult> {
        return try {
            val filterUrls =
                listOf(
                    "https://raw.githubusercontent.com/AdguardTeam/FiltersRegistry/master/filters/filter_2_Base/filter.txt",
                    "https://raw.githubusercontent.com/AdguardTeam/FiltersRegistry/master/filters/filter_11_Mobile/filter.txt",
                    "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/privacy.txt",
                    "https://raw.githubusercontent.com/Gatekeeper/filters/main/unhook.txt",
                )
            val rawLists = mutableListOf<String>()
            for (url in filterUrls) {
                try {
                    val response = client.get(url)
                    if (response.status.value in 200..299) {
                        rawLists.add(response.bodyAsText())
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    com.aegisgatekeeper.app.domain
                        .platformLog("Gatekeeper", "⚠️ Failed to fetch filter list: $url")
                }
            }
            if (rawLists.isEmpty()) {
                return loadBundledFilterRules()
            }
            parseFilterRules(rawLists).right()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            SyncError.NetworkFailure(e.message ?: "Unknown network failure").left()
        }
    }

    fun parseFilterRules(rawLists: List<String>): FilterRulesResult {
        val merged = mutableSetOf<String>()
        val unsupportedSelectors = listOf(":has(", ":xpath(", ":upward(", ":nth-ancestor(", ":remove()", ":style(")
        rawLists.forEach { list ->
            list.lines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("!")) return@forEach
                if (trimmed.startsWith("||") || trimmed.startsWith("@@||")) {
                    merged.add(trimmed)
                } else if (trimmed.contains("##")) {
                    val isUnsupported = unsupportedSelectors.any { trimmed.contains(it, ignoreCase = true) }
                    if (!isUnsupported) {
                        merged.add(trimmed)
                    }
                }
            }
        }
        val finalRules = merged.sorted()
        val hash =
            com.aegisgatekeeper.app.domain
                .computeHash(finalRules.joinToString("\n"))
        return FilterRulesResult(finalRules, hash)
    }

    suspend fun pullChanges(lastSyncTimestamp: Long = 0L): Either<SyncError, SyncPullPayload> {
        val token =
            GlobalDI.component.tokenProvider
                .getToken() ?: return SyncError.Unauthorized.left()
        val baseUrl =
            GlobalDI.component.tokenProvider
                .getSyncServerUrl()
                .trimEnd('/')

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
            if (e is kotlinx.coroutines.CancellationException) throw e
            SyncError.NetworkFailure(e.message ?: "Unknown network failure").left()
        }
    }
}
