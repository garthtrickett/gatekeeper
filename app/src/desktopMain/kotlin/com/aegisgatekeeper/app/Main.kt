package com.aegisgatekeeper.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import cafe.adriel.voyager.navigator.Navigator
import com.aegisgatekeeper.app.di.create
import com.aegisgatekeeper.app.domain.GatekeeperTheme
import com.aegisgatekeeper.app.navigation.DesktopRootScreen
import com.aegisgatekeeper.app.views.DesktopInterceptionOverlay
import dev.datlag.kcef.KCEF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun main() =
    application {
        com.aegisgatekeeper.app.db.DatabaseManager
            .init(
                com.aegisgatekeeper.app.db
                    .DesktopSqlDriverFactory(),
            )
        com.aegisgatekeeper.app.di.GlobalDI.component = com.aegisgatekeeper.app.di.DesktopApplicationComponent::class.create()

        var webViewReady by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                val osName = System.getProperty("os.name").lowercase()
                val userHome = System.getProperty("user.home")
                val cacheDirPath =
                    when {
                        osName.contains("win") -> {
                            System.getenv("APPDATA") + File.separator + "gatekeeper" + File.separator + "cache"
                        }

                        osName.contains("mac") -> {
                            userHome + File.separator + "Library" + File.separator + "Application Support" + File.separator +
                                "gatekeeper" +
                                File.separator +
                                "cache"
                        }

                        else -> {
                            userHome + File.separator + ".local" + File.separator + "share" +
                                File.separator + "gatekeeper" + File.separator + "cache"
                        }
                    }
                val cacheDir = File(cacheDirPath).apply { mkdirs() }

                KCEF.init(
                    builder = {
                        installDir(File("kcef-bundle"))
                        settings { cachePath = cacheDir.absolutePath }
                    },
                    onError = { it?.printStackTrace() },
                )
                webViewReady = true
                GatekeeperStateManager.dispatch(com.aegisgatekeeper.app.domain.GatekeeperAction.LoadInitialState)
                GatekeeperStateManager.dispatch(com.aegisgatekeeper.app.domain.GatekeeperAction.WebEngineInitialized)
            }
        }

        DisposableEffect(Unit) {
            onDispose { KCEF.disposeBlocking() }
        }

        val state by GatekeeperStateManager.state.collectAsState()

        LaunchedEffect(Unit) {
            System.getenv("GATEKEEPER_DEV_TOKEN")?.let { token ->
                GatekeeperStateManager.dispatch(
                    com.aegisgatekeeper.app.domain.GatekeeperAction
                        .LoginSuccess(token),
                )
            }
        }

        LaunchedEffect(state.sync.isAuthenticated) {
            if (state.sync.isAuthenticated) {
                withContext(Dispatchers.IO) {
                    val syncClient = com.aegisgatekeeper.app.di.GlobalDI.component.syncClient
                    while (true) {
                        try {
                            val pushPayload =
                                com.aegisgatekeeper.app.sync.SyncPushPayload(
                                    vaultItems =
                                        state.data.vaultItems.map {
                                            com.aegisgatekeeper.app.sync.VaultItemDto(
                                                it.id,
                                                it.query,
                                                it.capturedAtTimestamp,
                                                it.isResolved,
                                                it.lastModified,
                                                it.isDeleted,
                                            )
                                        },
                                    contentItems =
                                        state.data.contentItems.map {
                                            com.aegisgatekeeper.app.sync.ContentItemDto(
                                                it.id,
                                                it.podcastId,
                                                it.videoId,
                                                it.title,
                                                it.channelName,
                                                it.source.name,
                                                it.type.name,
                                                it.rank,
                                                it.capturedAtTimestamp,
                                                it.durationSeconds,
                                                it.lastModified,
                                                it.isDeleted,
                                            )
                                        },
                                )
                                                        syncClient.pushChanges(pushPayload)

                            val filterResult = syncClient.fetchFilterRules()
                            filterResult.fold(
                                ifLeft = { println("❌ Desktop: Filter rules pull failed: $it") },
                                ifRight = { rules ->
                                    GatekeeperStateManager.dispatch(com.aegisgatekeeper.app.domain.GatekeeperAction.UpdateFilterRules(rules))
                                    println("✅ Desktop: Downloaded ${rules.size} filter rules.")
                                }
                            )

                            val pullResult = syncClient.pullChanges(0L)
                            pullResult.fold(
                                ifLeft = {},
                                ifRight = { payload ->
                                    payload.vaultItems.forEach {
                                        if (it.isResolved) {
                                            println("Desktop received RESOLVED Vault Item!")
                                        } else {
                                            println("Desktop received synced Vault Item!")
                                        }
                                    }
                                    payload.contentItems.forEach {
                                        println("Desktop received synced Content Item!")
                                    }
                                    val newVaults =
                                        payload.vaultItems.map {
                                            com.aegisgatekeeper.app.domain.VaultItem(
                                                it.id,
                                                it.query,
                                                it.capturedAtTimestamp,
                                                it.isResolved,
                                                it.lastModified,
                                                true,
                                                it.isDeleted,
                                            )
                                        }
                                    val newContents =
                                        payload.contentItems.map {
                                            com.aegisgatekeeper.app.domain.ContentItem(
                                                it.id,
                                                it.podcastId,
                                                it.videoId,
                                                it.title,
                                                it.channelName,
                                                com.aegisgatekeeper.app.domain.ContentSource
                                                    .valueOf(it.source),
                                                com.aegisgatekeeper.app.domain.ContentType
                                                    .valueOf(it.type),
                                                it.rank,
                                                it.capturedAtTimestamp,
                                                it.durationSeconds,
                                                it.lastModified,
                                                true,
                                                it.isDeleted,
                                            )
                                        }
                                    GatekeeperStateManager.dispatch(
                                        com.aegisgatekeeper.app.domain.GatekeeperAction
                                            .RemoteSyncCompleted(newVaults, newContents),
                                    )
                                },
                            )
                        } catch (e: Exception) {
                        }
                        val pollDelay = if (System.getenv("GATEKEEPER_DEV_TOKEN") != null) 2000L else 15 * 60 * 1000L
                        kotlinx.coroutines.delay(pollDelay)
                    }
                }
            }
        }

        if (state.interception.isOverlayActive && state.interception.currentlyInterceptedApp != null) {
            Window(
                onCloseRequest = {},
                title = "Gatekeeper Interception",
                alwaysOnTop = true,
                undecorated = true,
                state = rememberWindowState(placement = WindowPlacement.Fullscreen),
            ) {
                GatekeeperTheme { DesktopInterceptionOverlay(interceptedApp = state.interception.currentlyInterceptedApp!!) }
            }
        }

        Window(onCloseRequest = ::exitApplication, title = "The Gatekeeper") {
            GatekeeperTheme {
                Navigator(DesktopRootScreen())
            }
        }
    }
