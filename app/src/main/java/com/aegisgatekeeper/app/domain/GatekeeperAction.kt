package com.aegisgatekeeper.app.domain

/**
 * A closed set of all possible intents or events in the system.
 */
sealed interface GatekeeperAction {
    // --- System Level OS Events ---
    data class AppBroughtToForeground(
        val packageName: String,
        val currentTimestamp: Long,
    ) : GatekeeperAction

    data class RuleViolationDetected(
        val packageName: String,
        val reason: String,
        val currentTimestamp: Long,
    ) : GatekeeperAction

    object DismissOverlay : GatekeeperAction

    // --- Layer Handoff Logic ---
    object LayerOmegaConnected : GatekeeperAction

    object LayerOmegaDisconnected : GatekeeperAction

    // --- Layer 1: Friction & Emergency Bypass ---
    data class FrictionCompleted(
        val packageName: String,
        val allocatedDurationMillis: Long,
        val currentTimestamp: Long,
    ) : GatekeeperAction

    data class EmergencyBypassRequested(
        val packageName: String,
        val reason: String,
        val allocatedDurationMillis: Long,
        val currentTimestamp: Long,
    ) : GatekeeperAction

    data class SessionExpired(
        val packageName: String,
        val allocatedDurationMillis: Long,
    ) : GatekeeperAction

    data class WhitelistExpired(
        val packageName: String,
    ) : GatekeeperAction

    data class SetCustomInterceptionMessage(
        val packageName: String,
        val message: String,
    ) : GatekeeperAction

    data class RemoveCustomInterceptionMessage(
        val packageName: String,
    ) : GatekeeperAction

    // --- Layer 2: The Lookup Vault ---
    data class SaveToVault(
        val query: String,
        val currentTimestamp: Long,
    ) : GatekeeperAction

    data class MarkVaultItemResolved(
        val id: String,
        val currentTimestamp: Long,
    ) : GatekeeperAction

    // --- Layer 3: The Content Bank ---
    data class ProcessSharedLink(
        val url: String,
        val providedTitle: String? = null,
        val currentTimestamp: Long,
    ) : GatekeeperAction

    data class SaveToContentBank(
        val videoId: String,
        val title: String,
        val source: ContentSource,
        val type: ContentType,
        val currentTimestamp: Long,
        val durationSeconds: Long? = null,
    ) : GatekeeperAction

    data class ReorderContentBank(
        val fromIndex: Int,
        val toIndex: Int,
        val currentTimestamp: Long,
    ) : GatekeeperAction

    data class RemoveFromContentBank(
        val id: String,
        val currentTimestamp: Long,
    ) : GatekeeperAction

    data class UpdateContentFilter(
        val filter: ContentType?,
    ) : GatekeeperAction

    // --- Layer 5: Clean Room Media Engines ---
    data class SearchYouTubeRequested(
        val query: String,
    ) : GatekeeperAction

    data class YouTubeSearchCompleted(
        val results: List<com.aegisgatekeeper.app.api.YoutubeSearchItem>,
    ) : GatekeeperAction

    data class YouTubeSearchFailed(
        val error: com.aegisgatekeeper.app.api.YoutubeError,
    ) : GatekeeperAction

    data class SaveMediaPosition(
        val mediaId: String,
        val positionSeconds: Float,
    ) : GatekeeperAction

    data class OpenCleanPlayer(
        val videoId: String,
    ) : GatekeeperAction

    object CloseCleanPlayer : GatekeeperAction

    // --- Intentional Content Slots ---
    data class SaveIntentionalSlot(
        val slotIndex: Int,
        val contentItem: ContentItem,
    ) : GatekeeperAction

    data class ClearIntentionalSlot(
        val slotIndex: Int,
    ) : GatekeeperAction

    data class OpenCleanAudioPlayer(
        val url: String,
    ) : GatekeeperAction

    object CloseCleanAudioPlayer : GatekeeperAction

    data class OpenSurgicalFacebook(
        val url: String = "https://m.facebook.com/groups/",
    ) : GatekeeperAction

    object CloseSurgicalFacebook : GatekeeperAction

    data class LogGiveUp(
        val packageName: String,
        val currentTimestamp: Long,
    ) : GatekeeperAction

    object GenerateExportData : GatekeeperAction

    data class ExportDataGenerated(
        val data: String,
    ) : GatekeeperAction

    object ClearExportData : GatekeeperAction

    // --- Triple Moat: Notification Interception ---
    data class LoadNotificationDigest(
        val logs: List<NotificationLog>,
    ) : GatekeeperAction

    object ClearNotificationDigest : GatekeeperAction

    // --- Post-Session Metacognition ---
    data class LogSessionMetacognition(
        val packageName: String,
        val durationMillis: Long,
        val emotion: Emotion,
        val currentTimestamp: Long,
    ) : GatekeeperAction

    // --- App Group & Blocking Rules ---
    data class CreateAppGroup(
        val id: String,
        val name: String,
        val apps: Set<String>,
        val combinator: RuleCombinator = RuleCombinator.ANY,
    ) : GatekeeperAction

    data class UpdateGroupCombinator(
        val groupId: String,
        val combinator: RuleCombinator,
    ) : GatekeeperAction

    data class UpdateGroupApps(
        val groupId: String,
        val apps: Set<String>,
    ) : GatekeeperAction

    data class DeleteAppGroup(
        val groupId: String,
    ) : GatekeeperAction

    data class AddDomainBlockRule(
        val id: String,
        val groupId: String,
        val domains: Set<String>,
    ) : GatekeeperAction

    data class UpdateDomainBlockRule(
        val ruleId: String,
        val groupId: String,
        val domains: Set<String>,
    ) : GatekeeperAction

    data class AddTimeLimitRule(
        val id: String,
        val groupId: String,
        val timeLimitMinutes: Int,
    ) : GatekeeperAction

    data class AddScheduledBlockRule(
        val id: String,
        val groupId: String,
        val timeSlots: List<com.aegisgatekeeper.app.domain.TimeSlot>,
        val daysOfWeek: Set<com.aegisgatekeeper.app.domain.DayOfWeek>,
    ) : GatekeeperAction

    data class AddCheckInRule(
        val id: String,
        val groupId: String,
        val checkInTimesMinutes: List<Int>,
        val durationMinutes: Int,
        val daysOfWeek: Set<com.aegisgatekeeper.app.domain.DayOfWeek>,
    ) : GatekeeperAction

    data class RedeemCheckInToken(
        val groupId: String,
        val checkInTimeMinutes: Int,
        val durationMinutes: Int,
        val reason: String?,
        val currentTimestamp: Long,
    ) : GatekeeperAction

    data class EndGroupSession(
        val groupId: String,
    ) : GatekeeperAction

    data class DeleteRule(
        val ruleId: String,
        val groupId: String,
    ) : GatekeeperAction

    data class ToggleRule(
        val ruleId: String,
        val groupId: String,
        val isEnabled: Boolean,
    ) : GatekeeperAction

    // --- Subscription Flow ---
    object UpgradeToProTier : GatekeeperAction

    data class SetFrictionGame(
        val game: com.aegisgatekeeper.app.domain.FrictionGame,
    ) : GatekeeperAction

    data class SetManualLockdown(
        val isActive: Boolean,
    ) : GatekeeperAction

    data class UpdateMissionControlApps(
        val packageNames: List<String>,
    ) : GatekeeperAction

    data class AddPinnedWebsite(
        val id: String,
        val label: String,
        val url: String,
    ) : GatekeeperAction

    data class RemovePinnedWebsite(
        val id: String,
    ) : GatekeeperAction

    data class OpenPinnedWebsite(
        val url: String,
    ) : GatekeeperAction

    object ClosePinnedWebsite : GatekeeperAction

    // --- Permission & Onboarding Flow ---
    data class PermissionsUpdated(
        val hasOverlay: Boolean,
        val hasUsageAccess: Boolean,
        val hasAccessibility: Boolean,
        val isBatteryDisabled: Boolean,
    ) : GatekeeperAction

    object ReengageShields : GatekeeperAction

    // --- Authentication Flow ---
    data class RequestMagicLink(
        val email: String,
    ) : GatekeeperAction

    data class LoginSuccess(
        val token: String,
    ) : GatekeeperAction

    object Logout : GatekeeperAction

    // --- Cross-Platform Sync ---
    data class RemoteSyncCompleted(
        val newVaultItems: List<VaultItem>,
        val newContentItems: List<ContentItem>,
    ) : GatekeeperAction

    data class UpdateSyncUrl(
        val url: String,
    ) : GatekeeperAction

    // --- Surgical Web Engine ---
    object WebEngineInitialized : GatekeeperAction

    data class SurgicalNavigationRequested(
        val url: String,
    ) : GatekeeperAction

    data class SurgicalNavigationCompleted(
        val url: String,
    ) : GatekeeperAction
}
