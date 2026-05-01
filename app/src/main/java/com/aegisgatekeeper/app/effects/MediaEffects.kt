package com.aegisgatekeeper.app.effects

import android.content.Intent
import android.util.Log
import com.aegisgatekeeper.app.App
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.api.RssClient
import com.aegisgatekeeper.app.api.UrlMetadataClient
import com.aegisgatekeeper.app.domain.ContentItem
import com.aegisgatekeeper.app.domain.ContentSource
import com.aegisgatekeeper.app.domain.ContentType
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperState
import com.aegisgatekeeper.app.domain.PodcastSubscription
import com.aegisgatekeeper.app.media.MediaDownloader
import com.aegisgatekeeper.app.widget.VaultWidget
import com.aegisgatekeeper.app.widget.updateAll
import kotlinx.coroutines.delay

suspend fun handleMediaAndSystemEffects(
    action: GatekeeperAction,
    oldState: GatekeeperState,
    newState: GatekeeperState,
    dispatch: (GatekeeperAction) -> Unit,
) {
    when (action) {
        is GatekeeperAction.SearchPodcastsRequested -> {
            Log.d("Gatekeeper", "📡 Searching Podcasts for '${action.query}'")
            (com.aegisgatekeeper.app.di.GlobalDI.component as com.aegisgatekeeper.app.di.AndroidApplicationComponent)
                .podcastIndexClient
                .searchPodcasts(
                    action.query,
                ).fold(
                    ifLeft = { error ->
                        Log.e("Gatekeeper", "❌ Podcast Search Failed: $error")
                        dispatch(GatekeeperAction.PodcastSearchCompleted(emptyList()))
                    },
                    ifRight = { results ->
                        dispatch(GatekeeperAction.PodcastSearchCompleted(results))
                    },
                )
        }

        is GatekeeperAction.DownloadMediaRequested -> {
            val item = newState.contentItems.find { it.id == action.id }
            if (item != null) {
                Log.d("Gatekeeper", "⬇️ Starting download for ${item.title}")
                com.aegisgatekeeper.app.media.MediaDownloader
                    .enqueueDownload(item.id, item.videoId)
            }
        }

        is GatekeeperAction.DeleteDownloadedMedia -> {
            Log.d("Gatekeeper", "🗑️ Deleting offline media for ${action.id}")
            com.aegisgatekeeper.app.media.MediaDownloader
                .removeDownload(action.id)
        }

        is GatekeeperAction.ProcessPodcastUrl -> {
            Log.i("Gatekeeper", "📡 Fetching Podcast RSS: ${action.url}")
            val result =
                (com.aegisgatekeeper.app.di.GlobalDI.component as com.aegisgatekeeper.app.di.AndroidApplicationComponent)
                    .rssClient
                    .fetchFeed(
                        action.url,
                    )
            result.fold(
                ifLeft = { error ->
                    Log.e("Gatekeeper", "❌ Failed to parse RSS: $error")
                    dispatch(GatekeeperAction.PodcastSyncFailed("Failed to parse RSS: $error"))
                },
                ifRight = { data ->
                    val podcastId =
                        java.util.UUID
                            .randomUUID()
                            .toString()
                    val sub =
                        PodcastSubscription(
                            id = podcastId,
                            feedUrl = action.url,
                            showTitle = data.title,
                            artworkUrl = data.artworkUrl,
                            lastModified = System.currentTimeMillis(),
                        )
                    dispatch(GatekeeperAction.SavePodcastSubscription(sub))
                },
            )
        }

        is GatekeeperAction.LoadPodcastEpisodes -> {
            Log.i("Gatekeeper", "📡 Loading Podcast Episodes from RSS: ${action.feedUrl}")
            val result =
                (com.aegisgatekeeper.app.di.GlobalDI.component as com.aegisgatekeeper.app.di.AndroidApplicationComponent)
                    .rssClient
                    .fetchFeed(
                        action.feedUrl,
                    )
            result.fold(
                ifLeft = { error ->
                    Log.e("Gatekeeper", "❌ Failed to load podcast episodes: $error")
                    if (newState.activePodcastEpisodes == null) {
                        dispatch(GatekeeperAction.ClearPodcastEpisodes)
                    } else {
                        dispatch(GatekeeperAction.PodcastEpisodesLoaded(newState.activePodcastEpisodes, action.podcastId))
                    }
                },
                ifRight = { data ->
                    dispatch(GatekeeperAction.CacheParsedEpisodes(data.episodes, action.podcastId))
                },
            )
        }

        is GatekeeperAction.AddEpisodeToBank -> {
            Log.i("Gatekeeper", "🎬 Adding episode to bank: ${action.episode.title}")
            dispatch(
                GatekeeperAction.SaveToContentBank(
                    videoId = action.episode.audioUrl,
                    title = action.episode.title,
                    source = ContentSource.GENERIC,
                    type = ContentType.AUDIO,
                    currentTimestamp = System.currentTimeMillis(),
                    durationSeconds = action.episode.durationSeconds,
                    channelName = action.podcastTitle,
                    podcastId = action.podcastId,
                ),
            )
        }

        is GatekeeperAction.SavePodcastSubscription -> {
            Log.i("Gatekeeper", "📡 Fetching episodes for new subscription: ${action.subscription.showTitle}")
            val result =
                (com.aegisgatekeeper.app.di.GlobalDI.component as com.aegisgatekeeper.app.di.AndroidApplicationComponent)
                    .rssClient
                    .fetchFeed(
                        action.subscription.feedUrl,
                    )
            result.fold(
                ifLeft = { error ->
                    Log.e("Gatekeeper", "❌ Failed to fetch feed for new subscription: $error")
                },
                ifRight = { data ->
                    dispatch(GatekeeperAction.CacheParsedEpisodes(data.episodes, action.subscription.id))
                },
            )
        }

        GatekeeperAction.RefreshAllFeedsRequested -> {
            dispatch(GatekeeperAction.PodcastSyncStarted)
            val request = androidx.work.OneTimeWorkRequestBuilder<com.aegisgatekeeper.app.sync.PodcastRefreshWorker>().build()
            androidx.work.WorkManager.getInstance(App.instance).enqueueUniqueWork(
                "manual-podcast-refresh",
                androidx.work.ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        is GatekeeperAction.ProcessSharedLink -> {
            Log.i("Gatekeeper", "Processing shared link: ${action.url}")
            val pattern = """(?<=youtu\.be/|watch\?v=|/shorts/)([a-zA-Z0-9_-]{11})""".toRegex()
            val videoId = pattern.find(action.url)?.value

            if (videoId != null) {
                dispatch(GatekeeperAction.ShowSurgicalSearch(action.url))
            } else if (action.url.contains("soundcloud.com", ignoreCase = true)) {
                val metadataResult =
                    (com.aegisgatekeeper.app.di.GlobalDI.component as com.aegisgatekeeper.app.di.AndroidApplicationComponent)
                        .urlMetadataClient
                        .fetchMetadata(
                            action.url,
                            isSoundCloud = true,
                        )
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
                // Generic link handling
                val metadataResult =
                    (com.aegisgatekeeper.app.di.GlobalDI.component as com.aegisgatekeeper.app.di.AndroidApplicationComponent)
                        .urlMetadataClient
                        .fetchMetadata(
                            action.url,
                            isGeneric = true,
                        )
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
            // Go to the home screen to prevent re-interception loop
            if (!com.aegisgatekeeper.app.App.isRunningTest) {
                val homeIntent =
                    Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                App.instance.startActivity(homeIntent)
            }
        }

                is GatekeeperAction.FrictionCompleted -> {
            Log.d("Gatekeeper", "⚙️ FrictionCompleted: Relaunching app to ensure it wasn't killed")
            if (!com.aegisgatekeeper.app.App.isRunningTest) {
                val launchIntent = App.instance.packageManager.getLaunchIntentForPackage(action.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    App.instance.startActivity(launchIntent)
                }
            }

            Log.d("Gatekeeper", "⚙️ FrictionCompleted: Scheduling SessionExpired in ${action.allocatedDurationMillis}ms")
            delay(action.allocatedDurationMillis)
            if (GatekeeperStateManager.state.value.activeForegroundApp == action.packageName) {
                dispatch(GatekeeperAction.SessionExpired(action.packageName, action.allocatedDurationMillis))
            }
        }

                is GatekeeperAction.EmergencyBypassRequested -> {
            Log.d("Gatekeeper", "⚙️ EmergencyBypassRequested: Relaunching app to ensure it wasn't killed")
            if (!com.aegisgatekeeper.app.App.isRunningTest) {
                val launchIntent = App.instance.packageManager.getLaunchIntentForPackage(action.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    App.instance.startActivity(launchIntent)
                }
            }

            Log.d("Gatekeeper", "⚙️ EmergencyBypassRequested: Scheduling SessionExpired in ${action.allocatedDurationMillis}ms")
            delay(action.allocatedDurationMillis)
            if (GatekeeperStateManager.state.value.activeForegroundApp == action.packageName) {
                dispatch(GatekeeperAction.SessionExpired(action.packageName, action.allocatedDurationMillis))
            }
        }

                is GatekeeperAction.RedeemCheckInToken -> {
            val group = newState.appGroups.find { it.id == action.groupId }
            val apps = group?.apps ?: emptySet()
            if (oldState.currentlyInterceptedApp in apps) {
                val packageName = oldState.currentlyInterceptedApp!!
                Log.d("Gatekeeper", "⚙️ RedeemCheckInToken: Relaunching app to ensure it wasn't killed")
                if (!com.aegisgatekeeper.app.App.isRunningTest) {
                    val launchIntent = App.instance.packageManager.getLaunchIntentForPackage(packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        App.instance.startActivity(launchIntent)
                    }
                }

                val durationMillis = action.durationMinutes * 60_000L
                Log.d("Gatekeeper", "⚙️ RedeemCheckInToken: Scheduling SessionExpired in ${durationMillis}ms")
                delay(durationMillis)
                if (GatekeeperStateManager.state.value.activeForegroundApp == packageName) {
                    dispatch(GatekeeperAction.SessionExpired(packageName, durationMillis))
                }
            }
        }

        is GatekeeperAction.SaveToContentBank,
        is GatekeeperAction.ReorderContentBank,
        is GatekeeperAction.RemoveFromContentBank,
        is GatekeeperAction.SaveIntentionalSlot,
        is GatekeeperAction.ClearIntentionalSlot,
        -> {
            try {
                VaultWidget().updateAll(App.instance)
            } catch (e: Exception) {
                Log.e("Gatekeeper", "Widget Update Failed", e)
            }
        }

        else -> { /* Other actions don't interact with media/system in this handler */ }
    }
}
