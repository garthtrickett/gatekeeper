package com.aegisgatekeeper.app

import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object GatekeeperStateManager {
    private val _state = MutableStateFlow(GatekeeperState())
    val state = _state.asStateFlow()

    fun dispatch(action: GatekeeperAction) {
        when (action) {
            is GatekeeperAction.DismissOverlay -> {
                _state.value =
                    _state.value.copy(
                        isOverlayActive = false,
                        currentlyInterceptedApp = null,
                    )
            }

            is GatekeeperAction.ClearNotificationDigest -> {
                _state.value =
                    _state.value.copy(
                        notificationDigest = emptyList(),
                    )
            }

            GatekeeperAction.WebEngineInitialized -> {
                _state.value =
                    _state.value.copy(
                        isWebEngineReady = true,
                    )
            }

            is GatekeeperAction.SurgicalNavigationRequested -> {
                _state.value =
                    _state.value.copy(
                        currentSurgicalUrl = action.url,
                    )
            }

            is GatekeeperAction.SurgicalNavigationCompleted -> {
                _state.value =
                    _state.value.copy(
                        currentSurgicalUrl = action.url,
                    )
            }

            // Auth actions are not implemented on Desktop MVP but need to be handled
            // to allow shared code to compile.
            is GatekeeperAction.RequestMagicLink -> {
                println("Desktop: Magic Link Requested for ${action.email}")
            }

            is GatekeeperAction.LoginSuccess -> {
                _state.value = _state.value.copy(isAuthenticated = true, jwtToken = action.token)
            }

            is GatekeeperAction.Logout -> {
                _state.value = _state.value.copy(isAuthenticated = false, jwtToken = null)
            }

            is GatekeeperAction.UpdateSyncUrl -> {
                _state.value = _state.value.copy(syncServerUrl = action.url)
            }

            is GatekeeperAction.RemoteSyncCompleted -> {
                val oldVaults = _state.value.vaultItems
                val oldContents = _state.value.contentItems

                _state.value =
                    _state.value.copy(
                        vaultItems = action.newVaultItems,
                        contentItems = action.newContentItems,
                    )

                val hasUnresolved = action.newVaultItems.any { it.query == "E2E Auto-Sync Test Item" && !it.isResolved }
                val hasResolved = action.newVaultItems.any { it.query == "E2E Auto-Sync Test Item" && it.isResolved }
                val hasContent = action.newContentItems.any { it.title == "E2E Video" }

                if (hasUnresolved && !oldVaults.any { it.query == "E2E Auto-Sync Test Item" && !it.isResolved }) {
                    println("✅ E2E_SUCCESS: Desktop received synced Vault Item!")
                }
                if (hasResolved && !oldVaults.any { it.query == "E2E Auto-Sync Test Item" && it.isResolved }) {
                    println("✅ E2E_SUCCESS: Desktop received RESOLVED Vault Item!")
                }
                if (hasContent && !oldContents.any { it.title == "E2E Video" }) {
                    println("✅ E2E_SUCCESS: Desktop received synced Content Item!")
                }
            }

            is GatekeeperAction.SaveMediaPosition -> {
                _state.value =
                    _state.value.copy(
                        savedMediaPositions = _state.value.savedMediaPositions + (action.mediaId to action.positionSeconds),
                    )
            }

            is GatekeeperAction.OpenPinnedWebsite -> {
                _state.value = _state.value.copy(activePinnedWebsiteUrl = action.url)
            }

            GatekeeperAction.ClosePinnedWebsite -> {
                _state.value = _state.value.copy(activePinnedWebsiteUrl = null)
            }

            is GatekeeperAction.AddPinnedWebsite -> {
                val newWebsite =
                    com.aegisgatekeeper.app.domain
                        .PinnedWebsite(action.id, action.label, action.url)
                _state.value = _state.value.copy(missionControlWebsites = _state.value.missionControlWebsites + newWebsite)
            }

            is GatekeeperAction.RemovePinnedWebsite -> {
                _state.value = _state.value.copy(missionControlWebsites = _state.value.missionControlWebsites.filter { it.id != action.id })
            }

            else -> { /* Not all actions are handled on desktop */ }
        }
    }
}
