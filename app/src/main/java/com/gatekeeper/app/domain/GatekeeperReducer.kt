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

        // --- Content Bank Logic ---
        is GatekeeperAction.ProcessSharedLink -> state // Pure side-effect trigger

        is GatekeeperAction.SaveToContentBank -> {
            val newRank = state.contentItems.size.toLong()
            val newItem = ContentItem(
                videoId = action.videoId,
                title = action.title,
                source = action.source,
                type = action.type,
                rank = newRank,
                capturedAtTimestamp = action.currentTimestamp,
            )
            state.copy(contentItems = state.contentItems + newItem)
        }

        is GatekeeperAction.ReorderContentBank -> {
            if (action.fromIndex !in state.contentItems.indices || action.toIndex !in state.contentItems.indices) {
                return state
            }
            val mutableList = state.contentItems.toMutableList()
            val itemToMove = mutableList.removeAt(action.fromIndex)
            mutableList.add(action.toIndex, itemToMove)

            // Reassign ranks based on new array indices
            val updatedList = mutableList.mapIndexed { index, item ->
                item.copy(rank = index.toLong())
            }
            state.copy(contentItems = updatedList)
        }

        is GatekeeperAction.RemoveFromContentBank -> {
            state.copy(contentItems = state.contentItems.filter { it.id != action.id })
        }

        // --- YouTube Clean Room Logic ---
        is GatekeeperAction.SearchYouTubeRequested -> {
            state.copy(isLoadingYouTube = true, youtubeSearchResults = emptyList())
        }

        is GatekeeperAction.YouTubeSearchCompleted -> {
            state.copy(isLoadingYouTube = false, youtubeSearchResults = action.results)
        }

        GatekeeperAction.YouTubeSearchFailed -> {
            state.copy(isLoadingYouTube = false)
        }

        is GatekeeperAction.OpenCleanPlayer -> {
            state.copy(activeVideoId = action.videoId)
        }

        GatekeeperAction.CloseCleanPlayer -> {
            state.copy(activeVideoId = null)
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
