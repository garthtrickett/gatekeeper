package com.aegisgatekeeper.app.effects

import com.aegisgatekeeper.app.domain.ContentSource
import com.aegisgatekeeper.app.domain.ContentType
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperEffect
import com.aegisgatekeeper.app.domain.GatekeeperState
import com.aegisgatekeeper.app.domain.PodcastSubscription
import com.aegisgatekeeper.app.domain.currentTimeMillis
import com.aegisgatekeeper.app.domain.platformLog
import com.aegisgatekeeper.app.domain.randomUUIDString
import com.aegisgatekeeper.app.media.MediaDownloader
import kotlinx.coroutines.delay

suspend fun executeMediaAndSystemEffect(
    effect: GatekeeperEffect,
    dispatch: (GatekeeperAction) -> Unit,
    effectHandler: PlatformEffectHandler,
) {
    when (effect) {
        is GatekeeperEffect.SearchPodcasts -> {
            platformLog("Gatekeeper", "📡 Searching Podcasts for '${effect.query}'")
            effectHandler.searchPodcasts(effect.query).fold(
                ifLeft = { error ->
                    platformLog("Gatekeeper", "❌ Podcast Search Failed: $error")
                    dispatch(GatekeeperAction.PodcastSearchCompleted(emptyList()))
                },
                ifRight = { results ->
                    dispatch(GatekeeperAction.PodcastSearchCompleted(results))
                },
            )
        }

        is GatekeeperEffect.DownloadMedia -> {
            platformLog("Gatekeeper", "⬇️ Starting download for ${effect.id}")
            MediaDownloader.enqueueDownload(effect.id, effect.url)
        }

        is GatekeeperEffect.DeleteDownloadedMedia -> {
            platformLog("Gatekeeper", "🗑️ Deleting offline media for ${effect.id}")
            MediaDownloader.removeDownload(effect.id)
        }

        is GatekeeperEffect.FetchPodcastFeedForSubscription -> {
            platformLog("Gatekeeper", "📡 Fetching Podcast RSS: ${effect.url}")
            effectHandler.fetchPodcastFeed(effect.url).fold(
                ifLeft = { error ->
                    platformLog("Gatekeeper", "❌ Failed to parse RSS: $error")
                    dispatch(GatekeeperAction.PodcastSyncFailed("Failed to parse RSS: $error"))
                },
                ifRight = { data ->
                    val podcastId = randomUUIDString()
                    val sub =
                        PodcastSubscription(
                            id = podcastId,
                            feedUrl = effect.url,
                            showTitle = data.title,
                            artworkUrl = data.artworkUrl,
                            lastModified = currentTimeMillis(),
                        )
                    dispatch(GatekeeperAction.SavePodcastSubscription(sub))
                },
            )
        }

        is GatekeeperEffect.FetchPodcastFeedForEpisodes -> {
            platformLog("Gatekeeper", "📡 Loading Podcast Episodes from RSS: ${effect.feedUrl}")
            effectHandler.fetchPodcastFeed(effect.feedUrl).fold(
                ifLeft = { error ->
                    platformLog("Gatekeeper", "❌ Failed to load podcast episodes: $error")
                },
                ifRight = { data ->
                    dispatch(GatekeeperAction.CacheParsedEpisodes(data.episodes, effect.podcastId))
                },
            )
        }

        is GatekeeperEffect.SchedulePodcastRefresh -> {
            effectHandler.schedulePodcastRefresh()
        }

        is GatekeeperEffect.FetchUrlMetadata -> {
            platformLog("Gatekeeper", "Processing shared link: ${effect.url}")
            val pattern = """(?<=youtu\.be/|watch\?v=|/shorts/)([a-zA-Z0-9_-]{11})""".toRegex()
            val videoId = pattern.find(effect.url)?.value

            if (videoId != null) {
                dispatch(
                    GatekeeperAction.SaveToContentBank(
                        videoId = videoId,
                        title = effect.providedTitle ?: "YouTube Video",
                        source = ContentSource.YOUTUBE,
                        type = ContentType.VIDEO,
                        currentTimestamp = effect.timestamp,
                    ),
                )
            } else if (effect.isSoundCloud) {
                val metadataResult = effectHandler.fetchUrlMetadata(effect.url, isSoundCloud = true, isGeneric = false)
                val title = metadataResult.fold({ effect.providedTitle ?: "SoundCloud Audio" }, { it.title })
                val durationSeconds = metadataResult.getOrNull()?.durationSeconds
                var resolvedUrl = metadataResult.getOrNull()?.resolvedUrl ?: effect.url
                resolvedUrl = resolvedUrl.replace("m.soundcloud.com", "soundcloud.com", ignoreCase = true)
                if (resolvedUrl.contains("?")) {
                    resolvedUrl = resolvedUrl.substringBefore("?")
                }

                dispatch(
                    GatekeeperAction.SaveToContentBank(
                        videoId = resolvedUrl,
                        title = title,
                        source = ContentSource.SOUNDCLOUD,
                        type = ContentType.AUDIO,
                        currentTimestamp = effect.timestamp,
                        durationSeconds = durationSeconds,
                    ),
                )
            } else {
                val metadataResult = effectHandler.fetchUrlMetadata(effect.url, isSoundCloud = false, isGeneric = true)
                val title = metadataResult.fold({ effect.providedTitle ?: "Saved Link" }, { it.title })
                val durationSeconds = metadataResult.getOrNull()?.durationSeconds
                val resolvedUrl = metadataResult.getOrNull()?.resolvedUrl ?: effect.url

                dispatch(
                    GatekeeperAction.SaveToContentBank(
                        videoId = resolvedUrl,
                        title = title,
                        source = ContentSource.GENERIC,
                        type = ContentType.READING,
                        currentTimestamp = effect.timestamp,
                        durationSeconds = durationSeconds,
                    ),
                )
            }
        }

        is GatekeeperEffect.GoHome -> {
            effectHandler.goHome()
        }

        is GatekeeperEffect.LaunchAppAndStartSession -> {
            platformLog("Gatekeeper", "⚙️ LaunchAppAndStartSession: Relaunching app to ensure it wasn't killed")
            effectHandler.launchApp(effect.packageName)
            platformLog("Gatekeeper", "⚙️ LaunchAppAndStartSession: Scheduling SessionExpired in ${effect.allocatedDurationMillis}ms")
            delay(effect.allocatedDurationMillis)
            dispatch(GatekeeperAction.SessionExpired(effect.packageName, effect.allocatedDurationMillis))
        }

        is GatekeeperEffect.TriggerWidgetUpdate -> {
            effectHandler.triggerWidgetUpdate()
        }

                else -> {}
    }
}
private fun old_handleMediaAndSystemEffects(
    action: GatekeeperAction,
    oldState: GatekeeperState,
    newState: GatekeeperState,
    dispatch: (GatekeeperAction) -> Unit,
    effectHandler: PlatformEffectHandler,
) {
    when (action) {
        is GatekeeperAction.SearchPodcastsRequested -> {
            platformLog("Gatekeeper", "📡 Searching Podcasts for '${action.query}'")
            effectHandler.searchPodcasts(action.query).fold(
                ifLeft = { error ->
                    platformLog("Gatekeeper", "❌ Podcast Search Failed: $error")
                    dispatch(GatekeeperAction.PodcastSearchCompleted(emptyList()))
                },
                ifRight = { results ->
                    dispatch(GatekeeperAction.PodcastSearchCompleted(results))
                },
            )
        }

        is GatekeeperAction.DownloadMediaRequested -> {
            val item = newState.data.contentItems.find { it.id == action.id }
            if (item != null) {
                platformLog("Gatekeeper", "⬇️ Starting download for ${item.title}")
                MediaDownloader.enqueueDownload(item.id, item.videoId)
            }
        }

        is GatekeeperAction.DeleteDownloadedMedia -> {
            platformLog("Gatekeeper", "🗑️ Deleting offline media for ${action.id}")
            MediaDownloader.removeDownload(action.id)
        }

        is GatekeeperAction.ProcessPodcastUrl -> {
            platformLog("Gatekeeper", "📡 Fetching Podcast RSS: ${action.url}")
            effectHandler.fetchPodcastFeed(action.url).fold(
                ifLeft = { error ->
                    platformLog("Gatekeeper", "❌ Failed to parse RSS: $error")
                    dispatch(GatekeeperAction.PodcastSyncFailed("Failed to parse RSS: $error"))
                },
                ifRight = { data ->
                    val podcastId = randomUUIDString()
                    val sub =
                        PodcastSubscription(
                            id = podcastId,
                            feedUrl = action.url,
                            showTitle = data.title,
                            artworkUrl = data.artworkUrl,
                            lastModified = currentTimeMillis(),
                        )
                    dispatch(GatekeeperAction.SavePodcastSubscription(sub))
                },
            )
        }

        is GatekeeperAction.LoadPodcastEpisodes -> {
            platformLog("Gatekeeper", "📡 Loading Podcast Episodes from RSS: ${action.feedUrl}")
            effectHandler.fetchPodcastFeed(action.feedUrl).fold(
                ifLeft = { error ->
                    platformLog("Gatekeeper", "❌ Failed to load podcast episodes: $error")
                    if (newState.media.activePodcastEpisodes == null) {
                        dispatch(GatekeeperAction.ClearPodcastEpisodes)
                    } else {
                        dispatch(GatekeeperAction.PodcastEpisodesLoaded(newState.media.activePodcastEpisodes!!, action.podcastId))
                    }
                },
                ifRight = { data ->
                    dispatch(GatekeeperAction.CacheParsedEpisodes(data.episodes, action.podcastId))
                },
            )
        }

        is GatekeeperAction.AddEpisodeToBank -> {
            platformLog("Gatekeeper", "🎬 Adding episode to bank: ${action.episode.title}")
            dispatch(
                GatekeeperAction.SaveToContentBank(
                    videoId = action.episode.audioUrl,
                    title = action.episode.title,
                    source = ContentSource.GENERIC,
                    type = ContentType.AUDIO,
                    currentTimestamp = currentTimeMillis(),
                    durationSeconds = action.episode.durationSeconds,
                    channelName = action.podcastTitle,
                    podcastId = action.podcastId,
                ),
            )
        }

        is GatekeeperAction.SavePodcastSubscription -> {
            platformLog("Gatekeeper", "📡 Fetching episodes for new subscription: ${action.subscription.showTitle}")
            effectHandler.fetchPodcastFeed(action.subscription.feedUrl).fold(
                ifLeft = { error ->
                    platformLog("Gatekeeper", "❌ Failed to fetch feed for new subscription: $error")
                },
                ifRight = { data ->
                    dispatch(GatekeeperAction.CacheParsedEpisodes(data.episodes, action.subscription.id))
                },
            )
        }

        GatekeeperAction.RefreshAllFeedsRequested -> {
            dispatch(GatekeeperAction.PodcastSyncStarted)
            effectHandler.schedulePodcastRefresh()
        }

        is GatekeeperAction.ProcessSharedLink -> {
            platformLog("Gatekeeper", "Processing shared link: ${action.url}")
            val pattern = """(?<=youtu\.be/|watch\?v=|/shorts/)([a-zA-Z0-9_-]{11})""".toRegex()
            val videoId = pattern.find(action.url)?.value

            if (videoId != null) {
                dispatch(
                    GatekeeperAction.SaveToContentBank(
                        videoId = videoId,
                        title = action.providedTitle ?: "YouTube Video",
                        source = com.aegisgatekeeper.app.domain.ContentSource.YOUTUBE,
                        type = com.aegisgatekeeper.app.domain.ContentType.VIDEO,
                        currentTimestamp = action.currentTimestamp,
                    ),
                )
            } else if (action.url.contains("soundcloud.com", ignoreCase = true)) {
                val metadataResult = effectHandler.fetchUrlMetadata(action.url, isSoundCloud = true, isGeneric = false)
                val title = metadataResult.fold({ action.providedTitle ?: "SoundCloud Audio" }, { it.title })
                val durationSeconds = metadataResult.getOrNull()?.durationSeconds
                var resolvedUrl = metadataResult.getOrNull()?.resolvedUrl ?: action.url
                resolvedUrl = resolvedUrl.replace("m.soundcloud.com", "soundcloud.com", ignoreCase = true)
                if (resolvedUrl.contains("?")) {
                    resolvedUrl = resolvedUrl.substringBefore("?")
                }

                dispatch(
                    GatekeeperAction.SaveToContentBank(
                        videoId = resolvedUrl,
                        title = title,
                        source = ContentSource.SOUNDCLOUD,
                        type = ContentType.AUDIO,
                        currentTimestamp = action.currentTimestamp,
                        durationSeconds = durationSeconds,
                    ),
                )
            } else {
                val metadataResult = effectHandler.fetchUrlMetadata(action.url, isSoundCloud = false, isGeneric = true)
                val title = metadataResult.fold({ action.providedTitle ?: "Saved Link" }, { it.title })
                val durationSeconds = metadataResult.getOrNull()?.durationSeconds
                val resolvedUrl = metadataResult.getOrNull()?.resolvedUrl ?: action.url

                dispatch(
                    GatekeeperAction.SaveToContentBank(
                        videoId = resolvedUrl,
                        title = title,
                        source = ContentSource.GENERIC,
                        type = ContentType.READING,
                        currentTimestamp = action.currentTimestamp,
                        durationSeconds = durationSeconds,
                    ),
                )
            }
        }

        is GatekeeperAction.LogGiveUp -> {
            effectHandler.goHome()
        }

        is GatekeeperAction.FrictionCompleted -> {
            platformLog("Gatekeeper", "⚙️ FrictionCompleted: Relaunching app to ensure it wasn't killed")
            effectHandler.launchApp(action.packageName)
            platformLog("Gatekeeper", "⚙️ FrictionCompleted: Scheduling SessionExpired in ${action.allocatedDurationMillis}ms")
            delay(action.allocatedDurationMillis)
            dispatch(GatekeeperAction.SessionExpired(action.packageName, action.allocatedDurationMillis))
        }

        is GatekeeperAction.EmergencyBypassRequested -> {
            platformLog("Gatekeeper", "⚙️ EmergencyBypassRequested: Relaunching app to ensure it wasn't killed")
            effectHandler.launchApp(action.packageName)
            platformLog("Gatekeeper", "⚙️ EmergencyBypassRequested: Scheduling SessionExpired in ${action.allocatedDurationMillis}ms")
            delay(action.allocatedDurationMillis)
            dispatch(GatekeeperAction.SessionExpired(action.packageName, action.allocatedDurationMillis))
        }

        is GatekeeperAction.RedeemCheckInToken -> {
            val group = newState.interception.appGroups.find { it.id == action.groupId }
            val apps = group?.apps ?: emptySet()
            if (oldState.interception.currentlyInterceptedApp in apps) {
                val packageName = oldState.interception.currentlyInterceptedApp!!
                platformLog("Gatekeeper", "⚙️ RedeemCheckInToken: Relaunching app to ensure it wasn't killed")
                effectHandler.launchApp(packageName)
                val durationMillis = action.durationMinutes * 60_000L
                platformLog("Gatekeeper", "⚙️ RedeemCheckInToken: Scheduling SessionExpired in ${durationMillis}ms")
                delay(durationMillis)
                dispatch(GatekeeperAction.SessionExpired(packageName, durationMillis))
            }
        }

        is GatekeeperAction.SaveToContentBank,
        is GatekeeperAction.ReorderContentBank,
        is GatekeeperAction.RemoveFromContentBank,
        is GatekeeperAction.SaveIntentionalSlot,
        is GatekeeperAction.ClearIntentionalSlot,
        -> {
            effectHandler.triggerWidgetUpdate()
        }

        else -> {}
    }
}
