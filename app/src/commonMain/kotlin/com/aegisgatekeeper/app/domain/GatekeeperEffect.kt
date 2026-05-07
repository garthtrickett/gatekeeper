package com.aegisgatekeeper.app.domain

import com.aegisgatekeeper.app.api.RssEpisode

sealed interface GatekeeperEffect {
    // Database Effects
    data class DbInsertVaultItem(
        val item: VaultItem,
    ) : GatekeeperEffect

    data class DbMarkVaultItemResolved(
        val id: String,
        val lastModified: Long,
    ) : GatekeeperEffect

    data class DbInsertPodcastSubscription(
        val subscription: PodcastSubscription,
    ) : GatekeeperEffect

    data class DbDeletePodcastSubscription(
        val id: String,
        val lastModified: Long,
    ) : GatekeeperEffect

    data class DbUpsertContentItem(
        val item: ContentItem,
    ) : GatekeeperEffect

    data class DbUpdateDownloadStatus(
        val id: String,
        val status: DownloadStatus,
        val localFilePath: String?,
        val lastModified: Long,
    ) : GatekeeperEffect

    data class DbUpdateRank(
        val id: String,
        val rank: Long,
        val lastModified: Long,
    ) : GatekeeperEffect

    data class DbRemoveFromContentBank(
        val id: String,
        val lastModified: Long,
    ) : GatekeeperEffect

    data class DbLoadCachedPodcastEpisodes(
        val podcastId: String,
    ) : GatekeeperEffect

    data class DbCacheParsedEpisodes(
        val episodes: List<RssEpisode>,
        val podcastId: String,
    ) : GatekeeperEffect

    object DbLoadLatestGlobalEpisodes : GatekeeperEffect

    data class DbInsertIntentionalSlot(
        val slotIndex: Int,
        val contentItemId: String,
    ) : GatekeeperEffect

    data class DbClearIntentionalSlot(
        val slotIndex: Int,
    ) : GatekeeperEffect

    data class DbCreateAppGroup(
        val id: String,
        val name: String,
        val apps: Set<String>,
        val combinator: RuleCombinator,
    ) : GatekeeperEffect

    data class DbUpdateGroupCombinator(
        val groupId: String,
        val combinator: RuleCombinator,
    ) : GatekeeperEffect

    data class DbUpdateGroupName(
        val groupId: String,
        val newName: String,
    ) : GatekeeperEffect

    data class DbUpdateGroupApps(
        val groupId: String,
        val apps: Set<String>,
    ) : GatekeeperEffect

    data class DbDeleteAppGroup(
        val groupId: String,
    ) : GatekeeperEffect

    data class DbAddAlwaysBlockRule(
        val id: String,
        val groupId: String,
    ) : GatekeeperEffect

    data class DbAddDomainBlockRule(
        val id: String,
        val groupId: String,
        val domains: Set<String>,
    ) : GatekeeperEffect

    data class DbUpdateDomainBlockRule(
        val ruleId: String,
        val domains: Set<String>,
    ) : GatekeeperEffect

    data class DbAddTimeLimitRule(
        val id: String,
        val groupId: String,
        val timeLimitMinutes: Int,
    ) : GatekeeperEffect

    data class DbAddScheduledBlockRule(
        val id: String,
        val groupId: String,
        val timeSlots: List<TimeSlot>,
        val daysOfWeek: Set<DayOfWeek>,
    ) : GatekeeperEffect

    data class DbAddCheckInRule(
        val id: String,
        val groupId: String,
        val checkInTimesMinutes: List<Int>,
        val durationMinutes: Int,
        val daysOfWeek: Set<DayOfWeek>,
    ) : GatekeeperEffect

    data class DbUpdateCheckInRule(
        val id: String,
        val checkInTimesMinutes: List<Int>,
        val durationMinutes: Int,
        val daysOfWeek: Set<DayOfWeek>,
    ) : GatekeeperEffect

    data class DbResetCheckIns(
        val groupId: String,
    ) : GatekeeperEffect

    data class DbRedeemCheckInToken(
        val log: ConsumedCheckIn?,
        val groupId: String,
        val reason: String?,
        val timestamp: Long,
    ) : GatekeeperEffect

    data class DbToggleSafeYouTubeChannel(
        val channelId: String,
        val channelName: String,
        val isSafe: Boolean,
    ) : GatekeeperEffect

    data class DbDeleteRule(
        val ruleId: String,
    ) : GatekeeperEffect

    data class DbToggleRule(
        val ruleId: String,
        val isEnabled: Boolean,
    ) : GatekeeperEffect

    data class DbUpdateProStatus(
        val isProTier: Boolean,
    ) : GatekeeperEffect

    data class DbSetFrictionGame(
        val game: FrictionGame,
    ) : GatekeeperEffect

    data class DbSetManualLockdown(
        val isActive: Boolean,
    ) : GatekeeperEffect

    data class DbUpdatePhaseWindows(
        val deepWorkStartMinutes: Int,
        val deepWorkEndMinutes: Int,
        val gatheringStartMinutes: Int,
        val gatheringEndMinutes: Int,
    ) : GatekeeperEffect

    object DbClearNotificationDigest : GatekeeperEffect

    data class DbInsertNotificationDigest(
        val log: NotificationLog,
    ) : GatekeeperEffect

    data class DbUpdateMissionControlApps(
        val packageNames: List<String>,
    ) : GatekeeperEffect

    data class DbLogGiveUp(
        val packageName: String,
        val timestamp: Long,
    ) : GatekeeperEffect

    object GenerateExportData : GatekeeperEffect

    data class DbInsertSessionLog(
        val log: SessionLog,
    ) : GatekeeperEffect

    data class DbSetCustomInterceptionMessage(
        val packageName: String,
        val message: String,
    ) : GatekeeperEffect

    data class DbRemoveCustomInterceptionMessage(
        val packageName: String,
    ) : GatekeeperEffect

    data class DbLogEmergencyBypass(
        val packageName: String,
        val reason: String,
        val timestamp: Long,
    ) : GatekeeperEffect

    data class DbSaveMediaPosition(
        val mediaId: String,
        val positionSeconds: Float,
    ) : GatekeeperEffect

    data class DbAddPinnedWebsite(
        val website: PinnedWebsite,
        val rank: Long,
    ) : GatekeeperEffect

    data class DbRemovePinnedWebsite(
        val id: String,
    ) : GatekeeperEffect

    data class DbAddAlternativeActivity(
        val activity: AlternativeActivity,
    ) : GatekeeperEffect

    data class DbRemoveAlternativeActivity(
        val id: String,
    ) : GatekeeperEffect

    data class DbInsertScheduledMessage(
        val message: ScheduledMessage,
    ) : GatekeeperEffect

    data class DbUpdateScheduledMessageStatus(
        val id: String,
        val status: MessageStatus,
    ) : GatekeeperEffect

    data class DbRemoteSyncCompleted(
        val newVaultItems: List<VaultItem>,
        val newContentItems: List<ContentItem>,
    ) : GatekeeperEffect

    object DbLoadInitialState : GatekeeperEffect

    // Media & System Effects
    data class SearchPodcasts(
        val query: String,
    ) : GatekeeperEffect

    data class DownloadMedia(
        val id: String,
        val url: String,
    ) : GatekeeperEffect

    data class DeleteDownloadedMedia(
        val id: String,
    ) : GatekeeperEffect

    data class FetchPodcastFeedForSubscription(
        val url: String,
    ) : GatekeeperEffect

    data class FetchPodcastFeedForEpisodes(
        val feedUrl: String,
        val podcastId: String,
    ) : GatekeeperEffect

    object SchedulePodcastRefresh : GatekeeperEffect

    data class FetchSurgicalStream(
        val item: ContentItem,
    ) : GatekeeperEffect

    data class FetchUrlMetadata(
        val url: String,
        val providedTitle: String?,
        val timestamp: Long,
        val isSoundCloud: Boolean,
        val isGeneric: Boolean,
    ) : GatekeeperEffect

    object GoHome : GatekeeperEffect

    data class LaunchAppAndStartSession(
        val packageName: String,
        val allocatedDurationMillis: Long,
    ) : GatekeeperEffect

    object TriggerWidgetUpdate : GatekeeperEffect

    // Integration Effects
    object SyncBeeperChats : GatekeeperEffect

    data class ScheduleBeeperMessage(
        val message: ScheduledMessage,
        val delayMillis: Long,
    ) : GatekeeperEffect

    // Sync & Auth Effects
    data class SaveToken(
        val token: String,
    ) : GatekeeperEffect

    object ClearToken : GatekeeperEffect

    data class RequestMagicLink(
        val email: String,
    ) : GatekeeperEffect

    // Utility Effects
    data class EmitAction(
        val action: GatekeeperAction,
    ) : GatekeeperEffect

    data class CompileFilterRules(
        val rules: List<String>,
    ) : GatekeeperEffect
}

data class Update<out S>(
    val state: S,
    val effects: Set<GatekeeperEffect> = emptySet(),
)
