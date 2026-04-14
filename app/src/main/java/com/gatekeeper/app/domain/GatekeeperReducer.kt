package com.gatekeeper.app.domain

/**
 * Pure top-level function. Evaluates actions and returns a new state.
 * No side effects allowed here (no DB calls, no clock reads, no API calls).
 */
fun reduce(
    state: GatekeeperState,
    action: GatekeeperAction,
): GatekeeperState =
    when (action) {
        is GatekeeperAction.AppBroughtToForeground -> {
            val isBlacklisted = state.blacklistedApps.contains(action.packageName)
            val whitelist = state.activeWhitelists[action.packageName]
            val isWhitelistValid = whitelist != null && action.currentTimestamp < whitelist.expiresAtTimestamp

            if (isBlacklisted && !isWhitelistValid) {
                // Trap sprung: App is blacklisted and has no valid whitelist.
                state.copy(
                    isOverlayActive = true,
                    currentlyInterceptedApp = action.packageName,
                )
            } else {
                // App is safe or actively whitelisted. Let them pass.
                state
            }
        }

        GatekeeperAction.DismissOverlay -> {
            state.copy(
                isOverlayActive = false,
                currentlyInterceptedApp = null,
            )
        }

        // --- Friction & Bypass Logic (The "Uber" Problem) ---
        is GatekeeperAction.EmergencyBypassRequested -> {
            // Grant a 5-minute (300,000 ms) whitelist
            val expiresAt = action.currentTimestamp + 300_000
            val newWhitelist =
                TemporaryWhitelist(
                    packageName = action.packageName,
                    reason = action.reason,
                    grantedAtTimestamp = action.currentTimestamp,
                    expiresAtTimestamp = expiresAt,
                )

            state.copy(
                isOverlayActive = false,
                currentlyInterceptedApp = null,
                activeWhitelists = state.activeWhitelists + (action.packageName to newWhitelist),
            )
        }

        is GatekeeperAction.FrictionCompleted -> {
            // Grant a 15-minute (900,000 ms) whitelist for completing hard friction
            val expiresAt = action.currentTimestamp + 900_000
            val newWhitelist =
                TemporaryWhitelist(
                    packageName = action.packageName,
                    reason = null,
                    grantedAtTimestamp = action.currentTimestamp,
                    expiresAtTimestamp = expiresAt,
                )

            state.copy(
                isOverlayActive = false,
                currentlyInterceptedApp = null,
                activeWhitelists = state.activeWhitelists + (action.packageName to newWhitelist),
            )
        }

        is GatekeeperAction.WhitelistExpired -> {
            // Clean up the map
            state.copy(
                activeWhitelists = state.activeWhitelists - action.packageName,
            )
        }

        // --- Vault Logic ---
        is GatekeeperAction.SaveToVault -> {
            val newItem =
                VaultItem(
                    query = action.query,
                    capturedAtTimestamp = action.currentTimestamp,
                )
            state.copy(vaultItems = state.vaultItems + newItem)
        }

        is GatekeeperAction.MarkVaultItemResolved -> {
            state.copy(
                vaultItems =
                    state.vaultItems.map {
                        if (it.id == action.id) it.copy(isResolved = true) else it
                    },
            )
        }

        // --- Metacognition Logic ---
        is GatekeeperAction.LogSessionMetacognition -> {
            val newLog =
                SessionLog(
                    packageName = action.packageName,
                    durationMillis = action.durationMillis,
                    emotion = action.emotion,
                    loggedAtTimestamp = action.currentTimestamp,
                )
            state.copy(sessionLogs = state.sessionLogs + newLog)
        }
    }
