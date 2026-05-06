package com.aegisgatekeeper.app

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperTheme
import com.aegisgatekeeper.app.services.PermissionChecker
import com.aegisgatekeeper.app.sync.SyncClient
import com.aegisgatekeeper.app.sync.SyncWorker
import com.aegisgatekeeper.app.views.ContentBankScreen
import com.aegisgatekeeper.app.views.VaultReviewScreen
import com.aegisgatekeeper.app.widget.VaultWidget
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntents(intent, isRecreation = false)
    }

    private fun handleIntents(
        intent: Intent,
        isRecreation: Boolean = false,
    ) {
        if (isRecreation) return

                val nativeAudioIdToPlay = intent.getStringExtra("OPEN_NATIVE_AUDIO_ID")
        val openActiveNativePlayer = intent.getBooleanExtra("OPEN_ACTIVE_NATIVE_PLAYER", false)
        val surgicalMediaIdToPlay = intent.getStringExtra("PLAY_SURGICAL_MEDIA_ID")

        intent.removeExtra("OPEN_NATIVE_AUDIO_ID")
        intent.removeExtra("OPEN_ACTIVE_NATIVE_PLAYER")
        intent.removeExtra("PLAY_SURGICAL_MEDIA_ID")

        if (surgicalMediaIdToPlay != null) {
            android.util.Log.d("Gatekeeper", "📺 MainActivity: Deep link received for Surgical Media (ID: $surgicalMediaIdToPlay)")
            val item = GatekeeperStateManager.state.value.data.contentItems.find { it.id == surgicalMediaIdToPlay }
            if (item != null) {
                GatekeeperStateManager.dispatch(GatekeeperAction.ExtractAndPlayMedia(item))
            }
        } else if (nativeAudioIdToPlay != null) {
            android.util.Log.d("Gatekeeper", "📺 MainActivity: Deep link received for Native Audio Player (ID: $nativeAudioIdToPlay)")
            val item =
                GatekeeperStateManager.state.value.data.contentItems
                    .find { it.id == nativeAudioIdToPlay }
            if (item != null) {
                GatekeeperStateManager.dispatch(GatekeeperAction.OpenNativePlayer(item))
            }
        } else if (openActiveNativePlayer) {
            android.util.Log.d("Gatekeeper", "📺 MainActivity: Deep link received for Active Native Audio Player")
            val item = GatekeeperStateManager.state.value.media.activeNativeMediaItem
            if (item != null) {
                GatekeeperStateManager.dispatch(GatekeeperAction.OpenNativePlayer(item))
            }
        } else if (youtubeVideoIdToPlay != null) {
            android.util.Log.d("Gatekeeper", "📺 MainActivity: Deep link received for YouTube Video (ID: $youtubeVideoIdToPlay)")
            GatekeeperStateManager.dispatch(GatekeeperAction.PlayYouTubeVideo(youtubeVideoIdToPlay))
        }

                if (surgicalMediaIdToPlay != null || nativeAudioIdToPlay != null || openActiveNativePlayer) {
            // Reset unmask state
            lifecycleScope.launch {
                try {
                    GlanceAppWidgetManager(this@MainActivity)
                        .getGlanceIds(VaultWidget::class.java)
                        .forEach { glanceId ->
                            updateAppWidgetState(this@MainActivity, glanceId) { prefs ->
                                prefs[booleanPreferencesKey("isUnmasked")] = false
                            }
                            VaultWidget().update(this@MainActivity, glanceId)
                        }
                } catch (e: Exception) {
                    // Ignore for tests
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Trigger an on-demand sync when the user opens the app
        val onDemandSyncRequest = OneTimeWorkRequestBuilder<SyncWorker>().build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "on-demand-sync",
            ExistingWorkPolicy.KEEP, // Don't queue up a new one if it's already running
            onDemandSyncRequest,
        )

        // Register device with backend for real-time FCM sync pokes
        lifecycleScope.launch {
            if (GatekeeperStateManager.state.value.sync.isAuthenticated) {
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        if (token != null) {
                            lifecycleScope.launch {
                                com.aegisgatekeeper.app.di.GlobalDI.component.syncClient
                                    .registerDevice(token)
                            }
                        }
                    }
                }
            }
        }

        // Auto-Advance Onboarding: Check permissions every time the app comes to the foreground
        val status = PermissionChecker.checkAll(this)
        GatekeeperStateManager.dispatch(
            GatekeeperAction.PermissionsUpdated(
                hasOverlay = status.hasOverlay,
                hasUsageAccess = status.hasUsageAccess,
                hasAccessibility = status.hasAccessibility,
                hasNotificationAccess = status.hasNotificationAccess,
                isBatteryDisabled = status.isBatteryDisabled,
            ),
        )

        // Auto-sync Beeper if permission already granted
        if (checkSelfPermission("com.beeper.android.permission.READ_PERMISSION") == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if (GatekeeperStateManager.state.value.sync.beeperChats
                    .isEmpty() && !GatekeeperStateManager.state.value.sync.isSyncingBeeper
            ) {
                GatekeeperStateManager.dispatch(GatekeeperAction.RequestBeeperSync)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }

        handleIntents(intent, savedInstanceState != null)

        if (BuildConfig.DEBUG) {
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context?,
                        intent: Intent?,
                    ) {
                        if (context == null || intent == null) return

                        if (intent.action == "com.aegisgatekeeper.E2E_ACTION") {
                            when (intent.getStringExtra("action")) {
                                "LOGIN" -> {
                                    val token = intent.getStringExtra("token") ?: return
                                    GatekeeperStateManager.dispatch(GatekeeperAction.LoginSuccess(token))
                                    android.util.Log.i("Gatekeeper", "✅ E2E: Auto-Logged in via ADB Intent")
                                }

                                "CREATE_VAULT" -> {
                                    val query = intent.getStringExtra("query") ?: "E2E Test Item"
                                    GatekeeperStateManager.dispatch(GatekeeperAction.SaveToVault(query, System.currentTimeMillis()))
                                    android.util.Log.i("Gatekeeper", "✅ E2E: Vault Item created via ADB Intent")
                                }

                                "CREATE_CONTENT" -> {
                                    val videoId = intent.getStringExtra("videoId") ?: "dQw4w9WgXcQ"
                                    GatekeeperStateManager.dispatch(
                                        GatekeeperAction.SaveToContentBank(
                                            videoId = videoId,
                                            title = "E2E Video",
                                            source = com.aegisgatekeeper.app.domain.ContentSource.YOUTUBE,
                                            type = com.aegisgatekeeper.app.domain.ContentType.VIDEO,
                                            currentTimestamp = System.currentTimeMillis(),
                                        ),
                                    )
                                    android.util.Log.i("Gatekeeper", "✅ E2E: Content Item created via ADB Intent")
                                }

                                "RESOLVE_VAULT" -> {
                                    val query = intent.getStringExtra("query") ?: "E2E Test Item"
                                    val item =
                                        GatekeeperStateManager.state.value.data.vaultItems
                                            .find { it.query == query }
                                    if (item != null) {
                                        GatekeeperStateManager.dispatch(
                                            GatekeeperAction.MarkVaultItemResolved(item.id, System.currentTimeMillis()),
                                        )
                                        android.util.Log.i("Gatekeeper", "✅ E2E: Vault Item resolved via ADB Intent")
                                    }
                                }

                                "FORCE_SYNC" -> {
                                    val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
                                    WorkManager.getInstance(context).enqueueUniqueWork("e2e-sync", ExistingWorkPolicy.REPLACE, request)
                                    android.util.Log.i("Gatekeeper", "✅ E2E: SyncWorker Enqueued via ADB Intent")
                                }
                            }
                        }
                    }
                }
            val filter = IntentFilter("com.aegisgatekeeper.E2E_ACTION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(receiver, filter)
            }
        }

        if (!com.aegisgatekeeper.app.App.isRunningTest) {
            setContent {
                GatekeeperTheme {
                    cafe.adriel.voyager.navigator
                        .Navigator(
                            com.aegisgatekeeper.app.navigation
                                .RootScreen(),
                        )
                }
            }
        }
    }
}
