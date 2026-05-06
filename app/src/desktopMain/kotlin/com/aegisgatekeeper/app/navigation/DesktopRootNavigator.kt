package com.aegisgatekeeper.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.aegisgatekeeper.app.views.*

abstract class DesktopGatekeeperTab(val title: String, val iconEmoji: String) : Tab {
    override val options: TabOptions
        @Composable
        get() = TabOptions(index = 0u, title = title)
}

object DesktopVaultTab : DesktopGatekeeperTab("Vault", "🔍") {
    @Composable override fun Content() = VaultReviewScreen()
}
object DesktopBankTab : DesktopGatekeeperTab("Bank", "🎬") {
    @Composable override fun Content() = ContentBankScreen()
}
object DesktopDigestTab : DesktopGatekeeperTab("Digest", "🔔") {
    @Composable override fun Content() = NotificationDigestScreen()
}
object DesktopWebTab : DesktopGatekeeperTab("Web", "🌐") {
    @Composable override fun Content() {
        val state by GatekeeperStateManager.state.collectAsState()
        if (state.media.isWebEngineReady) {
            SurgicalWebScreen()
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Initializing Surgical Web Engine...")
            }
        }
    }
}
object DesktopHabitsTab : DesktopGatekeeperTab("Habits", "🏃") {
    @Composable override fun Content() = AlternativeActivitiesScreen()
}
object DesktopAccountTab : DesktopGatekeeperTab("Account", "👤") {
    @Composable override fun Content() = AccountScreen()
}

class DesktopRootScreen : Screen {
    @Composable
    override fun Content() {
        val state by GatekeeperStateManager.state.collectAsState()
        val tabs = listOf(DesktopVaultTab, DesktopBankTab, DesktopDigestTab, DesktopWebTab, DesktopHabitsTab, DesktopAccountTab)

        TabNavigator(DesktopVaultTab) { tabNavigator ->
            Column(modifier = Modifier.fillMaxSize()) {
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
                            Text(tab.iconEmoji, fontSize = 24.sp, color = color)
                            Text(tab.title, fontSize = 12.sp, color = color, maxLines = 1)
                        }
                    }
                }
            }
        }

        if (state.media.activePinnedWebsiteUrl != null) {
            Surface(modifier = Modifier.fillMaxSize()) {
                PinnedWebModal(
                    url = state.media.activePinnedWebsiteUrl!!,
                    onClose = { GatekeeperStateManager.dispatch(GatekeeperAction.ClosePinnedWebsite) },
                )
            }
        }
    }
}