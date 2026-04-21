package com.aegisgatekeeper.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.aegisgatekeeper.app.domain.GatekeeperTheme
import com.aegisgatekeeper.app.sync.SyncClient
import com.aegisgatekeeper.app.views.CleanYouTubeScreen
import com.aegisgatekeeper.app.views.ContentBankScreen
import com.aegisgatekeeper.app.views.DesktopInterceptionOverlay
import com.aegisgatekeeper.app.views.NotificationDigestScreen
import com.aegisgatekeeper.app.views.VaultReviewScreen
import dev.datlag.kcef.KCEF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun main() =
    application {
        // Initialize Surgical Web Engine (Chromium)
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
                            userHome + File.separator +
                                "Library" +
                                File.separator +
                                "Application Support" +
                                File.separator +
                                "gatekeeper" +
                                File.separator +
                                "cache"
                        }

                        else -> {
                            userHome +
                                File.separator +
                                ".local" +
                                File.separator +
                                "share" +
                                File.separator +
                                "gatekeeper" +
                                File.separator +
                                "cache"
                        }
                    }
                val cacheDir = File(cacheDirPath).apply { mkdirs() }

                KCEF.init(
                    builder = {
                        installDir(File("kcef-bundle"))
                        progress { /* optional progress logs */ }
                        settings {
                            cachePath = cacheDir.absolutePath
                        }
                    },
                    onError = { it?.printStackTrace() },
                    onRestartRequired = { /* handle restart */ },
                )
                webViewReady = true
                GatekeeperStateManager.dispatch(com.aegisgatekeeper.app.domain.GatekeeperAction.WebEngineInitialized)
            }
        }

        DisposableEffect(Unit) {
            onDispose { KCEF.disposeBlocking() }
        }

        val state by GatekeeperStateManager.state.collectAsState()

        // E2E Auto-Login Injection
        LaunchedEffect(Unit) {
            System.getenv("GATEKEEPER_DEV_TOKEN")?.let { token ->
                GatekeeperStateManager.dispatch(
                    com.aegisgatekeeper.app.domain.GatekeeperAction
                        .LoginSuccess(token),
                )
                println("✅ E2E: Desktop Auto-Logged in via Environment Variable")
            }
        }

        // Initialize Offline-First Sync Engine Loop for Desktop
        LaunchedEffect(state.isAuthenticated) {
            if (state.isAuthenticated) {
                withContext(Dispatchers.IO) {
                    while (true) {
                        try {
                            val pushPayload =
                                com.aegisgatekeeper.app.sync.SyncPushPayload(
                                    vaultItems =
                                        state.vaultItems.map {
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
                                        state.contentItems.map {
                                            com.aegisgatekeeper.app.sync.ContentItemDto(
                                                it.id,
                                                it.videoId,
                                                it.title,
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
                            com.aegisgatekeeper.app.sync.SyncClient
                                .pushChanges(pushPayload)

                            val pullResult =
                                com.aegisgatekeeper.app.sync.SyncClient
                                    .pullChanges(0L)
                            pullResult.fold(
                                ifLeft = { error ->
                                    println("❌ SyncWorker: Pull failed: $error")
                                },
                                ifRight = { payload ->
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
                                                it.videoId,
                                                it.title,
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
                            e.printStackTrace()
                        }
                        // E2E Fast-Polling Override: Poll every 2 seconds if dev token is present
                        val pollDelay = if (System.getenv("GATEKEEPER_DEV_TOKEN") != null) 2000L else 15 * 60 * 1000L
                        kotlinx.coroutines.delay(pollDelay)
                    }
                }
            }
        }

        // The Interception Trigger: Instantly spawn a fullscreen blocking window
        if (state.isOverlayActive && state.currentlyInterceptedApp != null) {
            Window(
                onCloseRequest = { /* Blocked by design: User must complete task */ },
                title = "Gatekeeper Interception",
                alwaysOnTop = true,
                undecorated = true,
                state = rememberWindowState(placement = WindowPlacement.Fullscreen),
            ) {
                GatekeeperTheme {
                    DesktopInterceptionOverlay(interceptedApp = state.currentlyInterceptedApp!!)
                }
            }
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = "The Gatekeeper",
        ) {
            GatekeeperTheme {
                var selectedTab by remember { mutableIntStateOf(0) }

                val navItems =
                    listOf(
                        "Vault" to "🔍",
                        "Bank" to "🎬",
                        "Search" to "🎯",
                        "Digest" to "🔔",
                        "Web" to "🌐",
                        "Account" to "👤",
                    )

                Column(Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        // Reusing the exact same Compose UI from the Android target
                        when (selectedTab) {
                            0 -> {
                                VaultReviewScreen()
                            }

                            1 -> {
                                ContentBankScreen()
                            }

                            2 -> {
                                CleanYouTubeScreen()
                            }

                            3 -> {
                                NotificationDigestScreen()
                            }

                            4 -> {
                                if (state.isWebEngineReady) {
                                    com.aegisgatekeeper.app.views
                                        .SurgicalWebScreen()
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("Initializing Surgical Web Engine...")
                                    }
                                }
                            }

                            5 -> {
                                com.aegisgatekeeper.app.views
                                    .AccountScreen()
                            }
                        }
                    }

                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                    ) {
                        navItems.forEachIndexed { index, (label, icon) ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .clickable { selectedTab = index }
                                        .padding(vertical = 4.dp),
                            ) {
                                val color =
                                    if (selectedTab ==
                                        index
                                    ) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                Text(icon, fontSize = 24.sp, color = color)
                                Text(label, fontSize = 12.sp, color = color, maxLines = 1)
                            }
                        }
                    }
                }

                if (state.activePinnedWebsiteUrl != null) {
                    androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize()) {
                        com.aegisgatekeeper.app.views.PinnedWebModal(
                            url = state.activePinnedWebsiteUrl!!,
                            onClose = {
                                GatekeeperStateManager.dispatch(
                                    com.aegisgatekeeper.app.domain.GatekeeperAction.ClosePinnedWebsite,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
