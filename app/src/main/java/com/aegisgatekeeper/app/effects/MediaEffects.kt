package com.aegisgatekeeper.app.effects

import android.content.Intent
import android.util.Log
import com.aegisgatekeeper.app.App
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.api.UrlMetadataClient
import com.aegisgatekeeper.app.api.YoutubeApiClient
import com.aegisgatekeeper.app.domain.ContentSource
import com.aegisgatekeeper.app.domain.ContentType
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperState
import com.aegisgatekeeper.app.widget.VaultWidget
import com.aegisgatekeeper.app.widget.updateAll
import kotlinx.coroutines.delay

suspend fun handleMediaAndSystemEffects(
    action: GatekeeperAction,
    state: GatekeeperState,
    dispatch: (GatekeeperAction) -> Unit,
) {
    when (action) {
        is GatekeeperAction.ProcessSharedLink -> {
            Log.i("Gatekeeper", "Processing shared link: ${action.url}")
            val pattern = """(?<=youtu\.be/|watch\?v=|/shorts/)([a-zA-Z0-9_-]{11})""".toRegex()
            val videoId = pattern.find(action.url)?.value

            if (videoId != null) {
                var title: String? = action.providedTitle
                var channelName: String? = null
                var durationSeconds: Long? = null

                val detailsResult = YoutubeApiClient.getVideoDetails(videoId)
                detailsResult.fold(
                    ifLeft = { error ->
                        Log.w("Gatekeeper", "⚠️ YouTube API failed, falling back to HTML scrape: $error")
                        if (title == null) {
                            val metadataResult = UrlMetadataClient.fetchMetadata(action.url)
                            title = metadataResult.fold({ "YouTube Video" }, { it.title })
                        }
                    },
                    ifRight = { response ->
                        // Prefer the API title over the fallback text
                        val snippet = response.items.firstOrNull()?.snippet
                        title = snippet?.title ?: title
                        channelName = snippet?.channelTitle
                        val isoDuration =
                            response.items
                                .firstOrNull()
                                ?.contentDetails
                                ?.duration
                        if (isoDuration != null) {
                            durationSeconds =
                                com.aegisgatekeeper.app.domain
                                    .parseIso8601Duration(isoDuration)
                        }
                    },
                )

                dispatch(
                    GatekeeperAction.SaveToContentBank(
                        videoId = videoId,
                        title = title ?: "YouTube Video",
                        channelName = channelName,
                        source = ContentSource.YOUTUBE,
                        type = ContentType.VIDEO,
                        currentTimestamp = action.currentTimestamp,
                        durationSeconds = durationSeconds,
                    ),
                )
            } else if (action.url.contains("soundcloud.com", ignoreCase = true)) {
                val metadataResult = UrlMetadataClient.fetchMetadata(action.url, isSoundCloud = true)
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
                val metadataResult = UrlMetadataClient.fetchMetadata(action.url, isGeneric = true)
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

        is GatekeeperAction.SearchYouTubeRequested -> {
            Log.d("Gatekeeper", "📡 SearchYouTubeRequested: Calling YouTube API for '${action.query}'")
            YoutubeApiClient
                .searchVideos(action.query)
                .fold(
                    ifLeft = { error ->
                        Log.e("Gatekeeper", "❌ YouTube Search Failed: $error")
                        dispatch(GatekeeperAction.YouTubeSearchFailed(error))
                    },
                    ifRight = { response ->
                        dispatch(GatekeeperAction.YouTubeSearchCompleted(response.items))
                    },
                )
        }

        is GatekeeperAction.LogGiveUp -> {
            // Go to the home screen to prevent re-interception loop
            val homeIntent =
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            App.instance.startActivity(homeIntent)
        }

        is GatekeeperAction.FrictionCompleted -> {
            Log.d("Gatekeeper", "⚙️ FrictionCompleted: Relaunching app to ensure it wasn't killed")
            val launchIntent = App.instance.packageManager.getLaunchIntentForPackage(action.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                App.instance.startActivity(launchIntent)
            }

            Log.d("Gatekeeper", "⚙️ FrictionCompleted: Scheduling SessionExpired in ${action.allocatedDurationMillis}ms")
            delay(action.allocatedDurationMillis)
            if (GatekeeperStateManager.state.value.activeForegroundApp == action.packageName) {
                dispatch(GatekeeperAction.SessionExpired(action.packageName, action.allocatedDurationMillis))
            }
        }

        is GatekeeperAction.EmergencyBypassRequested -> {
            Log.d("Gatekeeper", "⚙️ EmergencyBypassRequested: Relaunching app to ensure it wasn't killed")
            val launchIntent = App.instance.packageManager.getLaunchIntentForPackage(action.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                App.instance.startActivity(launchIntent)
            }

            Log.d("Gatekeeper", "⚙️ EmergencyBypassRequested: Scheduling SessionExpired in ${action.allocatedDurationMillis}ms")
            delay(action.allocatedDurationMillis)
            if (GatekeeperStateManager.state.value.activeForegroundApp == action.packageName) {
                dispatch(GatekeeperAction.SessionExpired(action.packageName, action.allocatedDurationMillis))
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
