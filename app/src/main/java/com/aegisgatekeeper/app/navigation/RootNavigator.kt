package com.aegisgatekeeper.app.navigation

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.views.AccountScreen
import com.aegisgatekeeper.app.views.AlternativeActivitiesScreen
import com.aegisgatekeeper.app.views.AnalyticsScreen
import com.aegisgatekeeper.app.views.AppGroupsScreen
import com.aegisgatekeeper.app.views.ContentBankScreen
import com.aegisgatekeeper.app.views.IntentionalContentScreen
import com.aegisgatekeeper.app.views.MetacognitionDialog
import com.aegisgatekeeper.app.views.MissionControlScreen
import com.aegisgatekeeper.app.views.NativeAudioPlayerModal
import com.aegisgatekeeper.app.views.NotificationDigestScreen
import com.aegisgatekeeper.app.views.OutpostScreen
import com.aegisgatekeeper.app.views.PermissionsOnboardingScreen
import com.aegisgatekeeper.app.views.PinnedWebModal
import com.aegisgatekeeper.app.views.SurgicalFacebookScreen
import com.aegisgatekeeper.app.views.SurgicalWebScreen
import com.aegisgatekeeper.app.views.VaultReviewScreen

abstract class GatekeeperTab(
    val title: String,
    val iconEmoji: String,
) : Tab {
    override val options: TabOptions
        @Composable
        get() = TabOptions(index = 0u, title = title)
}

object HomeTab : GatekeeperTab("Home", "🏠") {
    @Composable override fun Content() = MissionControlScreen()
}

object VaultTab : GatekeeperTab("Vault", "🔍") {
    @Composable override fun Content() {
        val tabNavigator = LocalTabNavigator.current
        VaultReviewScreen(onNavigateToWeb = { tabNavigator.current = WebTab })
    }
}

object BankTab : GatekeeperTab("Bank", "🎬") {
    @Composable override fun Content() = ContentBankScreen()
}

object SlotsTab : GatekeeperTab("Slots", "📥") {
    @Composable override fun Content() = IntentionalContentScreen()
}

object OutpostTab : GatekeeperTab("Outpost", "🎯") {
    @Composable override fun Content() = OutpostScreen()
}

object DigestTab : GatekeeperTab("Digest", "🔔") {
    @Composable override fun Content() = NotificationDigestScreen()
}

object WebTab : GatekeeperTab("Web", "🌐") {
    @Composable override fun Content() = SurgicalWebScreen()
}

object RulesTab : GatekeeperTab("Rules", "🛡️") {
    @Composable override fun Content() = AppGroupsScreen()
}

object HabitsTab : GatekeeperTab("Habits", "🏃") {
    @Composable override fun Content() = AlternativeActivitiesScreen()
}

object InsightsTab : GatekeeperTab("Insights", "📊") {
    @Composable override fun Content() = AnalyticsScreen()
}

object AccountTab : GatekeeperTab("Account", "👤") {
    @Composable override fun Content() = AccountScreen()
}

class RootScreen : Screen {
    @Composable
    override fun Content() {
        val state by GatekeeperStateManager.state.collectAsState()

        if (!state.interception.isDualMoatEnabled) {
            PermissionsOnboardingScreen()
        } else {
            MainNavigationScreen().Content()
        }
    }
}

class MainNavigationScreen : Screen {
    @Composable
    override fun Content() {
        val state by GatekeeperStateManager.state.collectAsState()
        val isPro = state.sync.isProTier

        val tabs =
            listOf(
                HomeTab,
                VaultTab,
                BankTab,
                SlotsTab,
                OutpostTab,
                DigestTab,
                WebTab,
                RulesTab,
                HabitsTab,
                InsightsTab,
                AccountTab,
            )

        TabNavigator(HomeTab) { tabNavigator ->
            Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Box(modifier = Modifier.weight(1f)) {
                    CurrentTab()
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    tabs.forEach { tab ->
                        val isSelected = tabNavigator.current == tab
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f).clickable { tabNavigator.current = tab }.padding(vertical = 4.dp),
                        ) {
                            val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            val displayIcon =
                                when (tab) {
                                    BankTab -> if (isPro) tab.iconEmoji else "🔒"
                                    SlotsTab -> if (isPro) tab.iconEmoji else "🔒"
                                    InsightsTab -> if (isPro) tab.iconEmoji else "🔒"
                                    else -> tab.iconEmoji
                                }
                            Text(displayIcon, fontSize = 20.sp, color = color)
                            Text(tab.title, fontSize = 10.sp, color = color, maxLines = 1)
                        }
                    }
                }
            }
        }

        // Global Background Modals
        val activeNativeMediaItem = state.media.activeNativeMediaItem
        if (activeNativeMediaItem != null) {
            NativeAudioPlayerModal(
                contentItem = activeNativeMediaItem,
                isVisible = state.media.isNativePlayerMaximized,
                onMinimize = { GatekeeperStateManager.dispatch(GatekeeperAction.MinimizeNativePlayer) },
                onClose = { GatekeeperStateManager.dispatch(GatekeeperAction.CloseNativePlayer) },
            )
        }

        if (state.media.activeFacebookUrl != null) {
            SurgicalFacebookScreen(
                url = state.media.activeFacebookUrl!!,
                onClose = { GatekeeperStateManager.dispatch(GatekeeperAction.CloseSurgicalFacebook) },
            )
        }

        if (state.media.activePinnedWebsiteUrl != null) {
            PinnedWebModal(
                url = state.media.activePinnedWebsiteUrl!!,
                onClose = { GatekeeperStateManager.dispatch(GatekeeperAction.ClosePinnedWebsite) },
            )
        }

        if (state.data.pendingMetacognition != null) {
            MetacognitionDialog(
                request = state.data.pendingMetacognition!!,
                onDismiss = { GatekeeperStateManager.dispatch(GatekeeperAction.ClearMetacognition) },
            )
        }
    }
}
