package com.aegisgatekeeper.app.effects

import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import arrow.core.Either
import com.aegisgatekeeper.app.App
import com.aegisgatekeeper.app.api.ContentMetadata
import com.aegisgatekeeper.app.api.PodcastFeedDto
import com.aegisgatekeeper.app.api.PodcastIndexClient
import com.aegisgatekeeper.app.api.RssClient
import com.aegisgatekeeper.app.api.RssFeedData
import com.aegisgatekeeper.app.api.UrlMetadataClient
import com.aegisgatekeeper.app.domain.BeeperChat
import com.aegisgatekeeper.app.domain.ScheduledMessage
import com.aegisgatekeeper.app.domain.platformLog
import com.aegisgatekeeper.app.integrations.BeeperClient
import com.aegisgatekeeper.app.integrations.MessageDeliveryWorker
import com.aegisgatekeeper.app.sync.PodcastRefreshWorker
import com.aegisgatekeeper.app.widget.VaultWidget
import com.aegisgatekeeper.app.widget.updateAll
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import java.util.concurrent.TimeUnit

@Inject
class AndroidEffectHandler(
    private val context: Context,
    private val podcastIndexClient: PodcastIndexClient,
    private val rssClient: RssClient,
    private val urlMetadataClient: UrlMetadataClient,
    private val beeperClient: BeeperClient,
) : PlatformEffectHandler {
    override fun goHome() {
        if (!com.aegisgatekeeper.app.App.isRunningTest) {
            val homeIntent =
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            context.startActivity(homeIntent)
        }
    }

    override fun launchApp(packageName: String) {
        if (!com.aegisgatekeeper.app.App.isRunningTest) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                context.startActivity(launchIntent)
            }
        }
    }

    override fun triggerWidgetUpdate() {
        try {
            GlobalScope.launch {
                VaultWidget().updateAll(context)
            }
        } catch (e: Exception) {
            platformLog("Gatekeeper", "Widget Update Failed: ${e.message}")
        }
    }

    override fun schedulePodcastRefresh() {
        val request = OneTimeWorkRequestBuilder<PodcastRefreshWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "manual-podcast-refresh",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun scheduleBeeperMessage(
        message: ScheduledMessage,
        delayMillis: Long,
    ) {
        val data = Data.Builder().putString("messageId", message.id).build()
        val workRequest =
            OneTimeWorkRequestBuilder<MessageDeliveryWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }

    override suspend fun searchPodcasts(query: String): Either<String, List<PodcastFeedDto>> = podcastIndexClient.searchPodcasts(query)

    override suspend fun fetchPodcastFeed(url: String): Either<String, RssFeedData> = rssClient.fetchFeed(url)

    override suspend fun fetchUrlMetadata(
        url: String,
        isSoundCloud: Boolean,
        isGeneric: Boolean,
    ): Either<String, ContentMetadata> = urlMetadataClient.fetchMetadata(url, isSoundCloud, isGeneric).mapLeft { "Error fetching metadata" }

    override suspend fun syncBeeperChats(): Either<String, List<BeeperChat>> = beeperClient.getChats()
}
