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
            if (GatekeeperStateManager.state.value.isAuthenticated) {
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        if (token != null) {
                            lifecycleScope.launch {
                                SyncClient.registerDevice(token)
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
                isBatteryDisabled = status.isBatteryDisabled,
            ),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // UI Test Stability: Force the screen on and bypass the keyguard.
        // If the screen is off or locked, Compose will never perform a layout pass,
        // causing 'No compose hierarchies found' errors in tests.
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

        // Handle Deep Link from Widget
        val videoIdToPlay = intent.getStringExtra("OPEN_CLEAN_PLAYER_VIDEO_ID")
        val audioUrlToPlay = intent.getStringExtra("OPEN_CLEAN_AUDIO_URL")

        // E2E Programmatic State Injection (Debug Only)
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
                                        GatekeeperStateManager.state.value.vaultItems
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

        if (videoIdToPlay != null) {
            android.util.Log.d("Gatekeeper", "📺 MainActivity: Deep link received for Clean Player (Video: $videoIdToPlay)")
            GatekeeperStateManager.dispatch(GatekeeperAction.OpenCleanPlayer(videoIdToPlay))
        } else if (audioUrlToPlay != null) {
            android.util.Log.d("Gatekeeper", "📺 MainActivity: Deep link received for Clean Audio Player (URL: $audioUrlToPlay)")
            GatekeeperStateManager.dispatch(GatekeeperAction.OpenCleanAudioPlayer(audioUrlToPlay))
        }

        if (videoIdToPlay != null || audioUrlToPlay != null) {
            // Reset unmask state
            lifecycleScope.launch {
                try {
                    GlanceAppWidgetManager(this@MainActivity)
                        .getGlanceIds(VaultWidget::class.java)
                        .forEach { glanceId ->
                            updateAppWidgetState(this@MainActivity, glanceId) { prefs ->
                                prefs.toMutablePreferences().apply {
                                    set(booleanPreferencesKey("isUnmasked"), false)
                                }
                            }
                            VaultWidget().update(this@MainActivity, glanceId)
                        }
                } catch (e: Exception) {
                    // Ignore for tests
                }
            }
        }

        // Skip rendering the default UI if we are running instrumented tests.
        // This allows our tests to use MainActivity for its WakeLock properties
        // while calling composeTestRule.setContent { ... } to render specific isolated screens.
        val isRunningTest =
            try {
                Class.forName("androidx.test.espresso.Espresso")
                true
            } catch (e: ClassNotFoundException) {
                false
            }
        if (!isRunningTest) {
            setContent {
                GatekeeperTheme {
                    val state by GatekeeperStateManager.state.collectAsState()

                    if (!state.isDualMoatEnabled) {
                        com.aegisgatekeeper.app.views
                            .PermissionsOnboardingScreen()
                    } else {
                        var selectedTab by remember { mutableIntStateOf(0) }

                        val navItems =
                            listOf(
                                "Home" to "🏠",
                                "Vault" to "🔍",
                                "Bank" to (if (state.isProTier) "🎬" else "🔒"),
                                "Slots" to (if (state.isProTier) "🎯" else "🔒"),
                                "Web" to "🌐",
                                "Rules" to "🛡️",
                                "Insights" to (if (state.isProTier) "📊" else "🔒"),
                                "Account" to "👤",
                            )

                        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                            Box(modifier = Modifier.weight(1f)) {
                                when (selectedTab) {
                                    0 -> {
                                        com.aegisgatekeeper.app.views
                                            .MissionControlScreen()
                                    }

                                    1 -> {
                                        VaultReviewScreen()
                                    }

                                    2 -> {
                                        ContentBankScreen()
                                    }

                                    3 -> {
                                        com.aegisgatekeeper.app.views
                                            .IntentionalContentScreen()
                                    }

                                    4 -> {
                                        com.aegisgatekeeper.app.views
                                            .SurgicalWebScreen()
                                    }

                                    5 -> {
                                        com.aegisgatekeeper.app.views
                                            .AppGroupsScreen()
                                    }

                                    6 -> {
                                        com.aegisgatekeeper.app.views
                                            .AnalyticsScreen()
                                    }

                                    7 -> {
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
                                        Text(icon, fontSize = 20.sp, color = color)
                                        Text(label, fontSize = 10.sp, color = color, maxLines = 1)
                                    }
                                }
                            }
                        }

                        if (state.activeVideoId != null) {
                            com.aegisgatekeeper.app.views.CleanPlayerModal(
                                videoId = state.activeVideoId!!,
                                onClose = { GatekeeperStateManager.dispatch(GatekeeperAction.CloseCleanPlayer) },
                            )
                        }

                        if (state.activeAudioUrl != null) {
                            com.aegisgatekeeper.app.views.CleanAudioPlayerModal(
                                url = state.activeAudioUrl!!,
                                onClose = { GatekeeperStateManager.dispatch(GatekeeperAction.CloseCleanAudioPlayer) },
                            )
                        }

                        if (state.activeFacebookUrl != null) {
                            com.aegisgatekeeper.app.views.SurgicalFacebookScreen(
                                url = state.activeFacebookUrl!!,
                                onClose = { GatekeeperStateManager.dispatch(GatekeeperAction.CloseSurgicalFacebook) },
                            )
                        }

                        if (state.activePinnedWebsiteUrl != null) {
                            com.aegisgatekeeper.app.views.PinnedWebModal(
                                url = state.activePinnedWebsiteUrl!!,
                                onClose = { GatekeeperStateManager.dispatch(GatekeeperAction.ClosePinnedWebsite) },
                            )
                        }
                    }
                }
            }
        }
    }
}
