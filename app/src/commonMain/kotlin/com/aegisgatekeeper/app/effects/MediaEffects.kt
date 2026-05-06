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
                val metadataResult = effectHandler.fetchUrlMetadata(effect.url, isSoundCloud = false, isGeneric = false)
                val title = metadataResult.fold({ effect.providedTitle ?: "YouTube Video" }, { it.title })

                dispatch(
                    GatekeeperAction.SaveToContentBank(
                        videoId = videoId,
                        title = title,
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

                is GatekeeperEffect.FetchSurgicalStream -> {
            platformLog("Gatekeeper", "📡 Extracting surgical stream for ${effect.item.videoId}")
            effectHandler.fetchSurgicalStream(effect.item).fold(
                ifLeft = { error ->
                    platformLog("Gatekeeper", "❌ Surgical Extraction Failed: $error")
                    dispatch(GatekeeperAction.SurgicalExtractionFailed(effect.item.id, error))
                },
                ifRight = { item ->
                    dispatch(GatekeeperAction.OpenNativePlayer(item))
                },
            )
        }

        else -> {}
    }
}
