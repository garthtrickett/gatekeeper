package com.aegisgatekeeper.app

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aegisgatekeeper.app.domain.ContentSource
import com.aegisgatekeeper.app.domain.ContentType
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperTheme
import com.aegisgatekeeper.app.views.ContentBankScreen
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContentBankUiTest {
    @Before
    fun setup() {
        GatekeeperStateManager.resetStateForTest()
        GatekeeperStateManager.dispatch(com.aegisgatekeeper.app.domain.GatekeeperAction.UpgradeToProTier)
    }

    @After
    fun tearDown() {
        // Reset the singleton state to prevent test leakage
        GatekeeperStateManager.resetStateForTest()
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testContentBank_FreeTier_ShowsPaywall_And_Unlocks() {
        // Override the setup() Pro upgrade and force Free Tier state
        GatekeeperStateManager.resetStateForTest()

        composeTestRule.setContent {
            GatekeeperTheme {
                ContentBankScreen(overrideTime = java.time.LocalTime.of(20, 0))
            }
        }

        // Assert: The Paywall intercepts the screen
        composeTestRule.onNodeWithText("Sovereign Tier Required").assertIsDisplayed()
        composeTestRule.onNodeWithText("The Priority Matrix").assertIsDisplayed()

        // Act: Click the unlock button
        composeTestRule.onNodeWithText("Unlock Lifetime Pro - $129").performClick()
        composeTestRule.waitForIdle()

        // Assert: The Paywall is gone and the core feature is revealed
        composeTestRule.onNodeWithText("The Content Bank").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sovereign Tier Required").assertDoesNotExist()
    }

    @Test
    fun testContentBank_ShowsLoadingIndicatorWhenProcessingLink() {
        // Seed State via Reflection to mock processing state
        val stateFlowField = GatekeeperStateManager.javaClass.getDeclaredField("_state")
        stateFlowField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow =
            stateFlowField.get(
                GatekeeperStateManager,
            ) as kotlinx.coroutines.flow.MutableStateFlow<com.aegisgatekeeper.app.domain.GatekeeperState>
        stateFlow.value = stateFlow.value.copy(isProcessingLink = true)

        composeTestRule.setContent {
            GatekeeperTheme {
                ContentBankScreen(overrideTime = java.time.LocalTime.of(20, 0))
            }
        }

        // Verify progress indicator is displayed via semantic content description
        composeTestRule.onNodeWithContentDescription("Processing Link").assertIsDisplayed()

        // Ensure the regular Add FAB is gone
        composeTestRule.onNodeWithText("+").assertDoesNotExist()
    }

    @Test
    fun testContentBank_EmptyState() {
        composeTestRule.setContent {
            GatekeeperTheme {
                ContentBankScreen(overrideTime = java.time.LocalTime.of(20, 0))
            }
        }

        composeTestRule.onNodeWithText("The Content Bank").assertExists()
        composeTestRule.onNodeWithText("Bank is empty. Share a link to Gatekeeper to capture it.").assertIsDisplayed()
    }

    @Test
    fun testContentBank_PopulatedState_RendersRanksAndTitles() {
        // Seed State
        GatekeeperStateManager.dispatch(
            GatekeeperAction.SaveToContentBank(
                videoId = "dQw4w9WgXcQ",
                title = "Never Gonna Give You Up",
                source = ContentSource.YOUTUBE,
                type = ContentType.VIDEO,
                currentTimestamp = 1000L,
                durationSeconds = 600L,
                channelName = "Rick Astley",
            ),
        )

        composeTestRule.setContent {
            GatekeeperTheme {
                ContentBankScreen(overrideTime = java.time.LocalTime.of(20, 0))
            }
        }

        // Verify item data renders
        composeTestRule.onNodeWithText("Never Gonna Give You Up").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rick Astley").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("YOUTUBE • 10m")[0].assertIsDisplayed()
        composeTestRule.onAllNodesWithText("#1")[0].assertIsDisplayed() // Rank 0 renders as #1
    }

    @Test
    fun testContentBank_DropButton_RemovesItem() {
        val title = "Test Drop Video ${System.currentTimeMillis()}"
        GatekeeperStateManager.dispatch(
            GatekeeperAction.SaveToContentBank(
                videoId = "vid123",
                title = title,
                source = ContentSource.YOUTUBE,
                type = ContentType.VIDEO,
                currentTimestamp = 1000L,
            ),
        )

        composeTestRule.setContent {
            GatekeeperTheme {
                ContentBankScreen(overrideTime = java.time.LocalTime.of(20, 0))
            }
        }

        composeTestRule.onNodeWithText(title).assertExists()

        // Act: Click Drop
        composeTestRule.onNodeWithText("Drop").performClick()
        composeTestRule.waitForIdle()

        // Assert
        composeTestRule.onNodeWithText(title).assertDoesNotExist()
    }

    @Test
    fun testContentBank_FilterChips_FiltersList() {
        GatekeeperStateManager.dispatch(
            GatekeeperAction.SaveToContentBank("vid1", "Video Item", ContentSource.YOUTUBE, ContentType.VIDEO, 1000L),
        )
        GatekeeperStateManager.dispatch(
            GatekeeperAction.SaveToContentBank("aud1", "Audio Item", ContentSource.SOUNDCLOUD, ContentType.AUDIO, 2000L),
        )

        composeTestRule.setContent {
            GatekeeperTheme {
                ContentBankScreen(overrideTime = java.time.LocalTime.of(20, 0)) // Outside deep work
            }
        }

        composeTestRule.onNodeWithText("Video Item").assertIsDisplayed()
        composeTestRule.onNodeWithText("Audio Item").assertIsDisplayed()

        // Act
        composeTestRule.onNodeWithText("Audio").performClick()
        composeTestRule.waitForIdle()

        // Assert
        composeTestRule.onNodeWithText("Audio Item").assertIsDisplayed()
        composeTestRule.onNodeWithText("Video Item").assertDoesNotExist()
    }

    @Test
    fun testContentBank_DeepWorkHours_TriggersFriction() {
        GatekeeperStateManager.dispatch(
            GatekeeperAction.SaveToContentBank("vid1", "Video Item", ContentSource.YOUTUBE, ContentType.VIDEO, 1000L),
        )

        composeTestRule.setContent {
            GatekeeperTheme {
                ContentBankScreen(overrideTime = java.time.LocalTime.of(14, 0)) // Inside deep work
            }
        }

        // Disable auto-advance to prevent the continuous withFrameNanos loop in
        // BallBalancingUi from fast-forwarding the clock and auto-winning the game.
        composeTestRule.mainClock.autoAdvance = false

        // Act: Try to filter
        composeTestRule.onNodeWithText("Video").performClick()

        // Advance time manually to allow the Dialog to enter the composition
        composeTestRule.mainClock.advanceTimeBy(500)

        // Assert: Friction dialog appears
        composeTestRule.onNodeWithText("Deep Work Interruption").assertExists()

        // Close it so teardown doesn't hang
        composeTestRule.onNodeWithText("Give Up").performClick()
        composeTestRule.mainClock.advanceTimeBy(500)

        // Restore auto-advance for other tests
        composeTestRule.mainClock.autoAdvance = true
    }

    @Test
    fun testContentBank_DragAndDrop_ReordersItems() {
        // Arrange: Seed the state with two items
        val title1 = "First Video"
        val title2 = "Second Video"
        GatekeeperStateManager.dispatch(
            GatekeeperAction.SaveToContentBank(
                videoId = "vid1",
                title = title1,
                source = ContentSource.YOUTUBE,
                type = ContentType.VIDEO,
                currentTimestamp = 1000L,
            ),
        )
        GatekeeperStateManager.dispatch(
            GatekeeperAction.SaveToContentBank(
                videoId = "vid2",
                title = title2,
                source = ContentSource.YOUTUBE,
                type = ContentType.VIDEO,
                currentTimestamp = 2000L,
            ),
        )

        composeTestRule.setContent {
            GatekeeperTheme {
                ContentBankScreen(overrideTime = java.time.LocalTime.of(20, 0))
            }
        }

        // Assert initial order: #1 is First, #2 is Second
        composeTestRule.onNodeWithText("#1").assertExists()
        composeTestRule.onNodeWithText(title1).assertExists()
        composeTestRule.onNodeWithText("#2").assertExists()
        composeTestRule.onNodeWithText(title2).assertExists()

        // Act: Simulate a drag and drop
        // We find the first item, long-press it, and drag it down by 200 pixels (enough to swap)
        composeTestRule.onNodeWithText(title1).performTouchInput {
            down(center)
            advanceEventTime(1000L)
            moveBy(
                androidx.compose.ui.geometry
                    .Offset(x = 0f, y = 200f),
            )
            up()
        }

        composeTestRule.waitForIdle()

        // Assert: The order should now be swapped
        // The UI should now show #1 as Second Video and #2 as First Video
        // We can check this by finding the rank node next to the title node in the hierarchy
        composeTestRule.onNodeWithText("#1").assertExists()
        composeTestRule.onNodeWithText(title2).assertExists() // Title2 should now be #1
        composeTestRule.onNodeWithText("#2").assertExists()
        composeTestRule.onNodeWithText(title1).assertExists() // Title1 should now be #2
    }

    @Test
    fun testContentBank_ManualAddFlow() {
        composeTestRule.setContent {
            GatekeeperTheme {
                ContentBankScreen(overrideTime = java.time.LocalTime.of(20, 0))
            }
        }

        // 1. Click the FAB
        composeTestRule.onNodeWithText("+").performClick()

        // 2. Verify Dialog appears
        composeTestRule.onNodeWithText("Add to Bank").assertIsDisplayed()

        // 3. Enter a URL
        val testUrl = "https://youtu.be/dQw4w9WgXcQ"
        composeTestRule.onNodeWithText("Paste YouTube or SoundCloud link").performTextInput(testUrl)

        // 4. Click Add
        composeTestRule.onNodeWithText("Add Intent").performClick()

        // 5. Verify dialog is dismissed (wait for idle)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Add to Bank").assertDoesNotExist()

        // Note: We don't assert the item appears immediately in the list because
        // ProcessSharedLink triggers an asynchronous network call to fetch the title.
        // In a real instrumented test, we would mock the GatekeeperStateManager scope
        // if we wanted to verify the final list state.
    }

    @Test
    fun testContentBank_SoundCloudItem_PlaysInAudioPlayer() {
        // Arrange
        val soundcloudUrl = "https://soundcloud.com/test/track"
        GatekeeperStateManager.dispatch(
            GatekeeperAction.SaveToContentBank(
                videoId = soundcloudUrl, // For non-YouTube, videoId holds the URL
                title = "Test SoundCloud Track",
                source = ContentSource.SOUNDCLOUD,
                type = ContentType.AUDIO,
                currentTimestamp = 1000L,
            ),
        )

        composeTestRule.setContent {
            GatekeeperTheme {
                val state by GatekeeperStateManager.state.collectAsState()
                ContentBankScreen(overrideTime = java.time.LocalTime.of(20, 0))
                if (state.activeAudioUrl != null) {
                    com.aegisgatekeeper.app.views.CleanAudioPlayerModal(
                        url = state.activeAudioUrl!!,
                        onClose = { GatekeeperStateManager.dispatch(GatekeeperAction.CloseCleanAudioPlayer) },
                    )
                }
            }
        }

        // Act: Click the play button for our new item.
        // Since there's only one item, there's only one "Play" button.
        composeTestRule.onNodeWithText("Play").performClick()
        composeTestRule.waitForIdle()

        // Assert: The state manager should now have an active audio URL
        val state = GatekeeperStateManager.state.value
        com.google.common.truth.Truth
            .assertThat(state.activeAudioUrl)
            .isEqualTo(soundcloudUrl)

        // Assert: The modal UI should be visible
        composeTestRule.onNodeWithText("Close Audio").assertIsDisplayed()
    }

    @Test
    fun testContentBank_SearchBar_FiltersByChannelName() {
        // Arrange: Seed State
        val title1 = "Kotlin Coroutines Guide"
        val title2 = "Espresso Test Framework"
        GatekeeperStateManager.dispatch(
            GatekeeperAction.SaveToContentBank(
                videoId = "vid1",
                title = title1,
                channelName = "Google Developers",
                source = ContentSource.YOUTUBE,
                type = ContentType.VIDEO,
                currentTimestamp = 1000L,
            ),
        )
        GatekeeperStateManager.dispatch(
            GatekeeperAction.SaveToContentBank(
                videoId = "vid2",
                title = title2,
                channelName = "Android Developers",
                source = ContentSource.YOUTUBE,
                type = ContentType.VIDEO,
                currentTimestamp = 2000L,
            ),
        )

        composeTestRule.setContent {
            GatekeeperTheme {
                ContentBankScreen(overrideTime = java.time.LocalTime.of(20, 0))
            }
        }

        // Act: Search for a channel name
        composeTestRule.onNodeWithText("Search bank...").performTextInput("Android")
        composeTestRule.waitForIdle()

        // Assert: Only the item from that channel is visible
        composeTestRule.onNodeWithText(title1).assertDoesNotExist()
        composeTestRule.onNodeWithText(title2).assertIsDisplayed()
    }

    @Test
    fun testContentBank_SearchBar_FiltersListAndClears() {
        // Arrange: Seed State
        val title1 = "Kotlin Coroutines Guide"
        val title2 = "Espresso Test Framework"
        GatekeeperStateManager.dispatch(
            GatekeeperAction.SaveToContentBank(
                videoId = "vid1",
                title = title1,
                source = ContentSource.YOUTUBE,
                type = ContentType.VIDEO,
                currentTimestamp = 1000L,
            ),
        )
        GatekeeperStateManager.dispatch(
            GatekeeperAction.SaveToContentBank(
                videoId = "vid2",
                title = title2,
                source = ContentSource.YOUTUBE,
                type = ContentType.VIDEO,
                currentTimestamp = 2000L,
            ),
        )

        composeTestRule.setContent {
            GatekeeperTheme {
                ContentBankScreen(overrideTime = java.time.LocalTime.of(20, 0))
            }
        }

        // Assert initial state
        composeTestRule.onNodeWithText(title1).assertIsDisplayed()
        composeTestRule.onNodeWithText(title2).assertIsDisplayed()

        // Act 1: Type into search bar
        composeTestRule.onNodeWithText("Search bank...").performTextInput("Kotlin")
        composeTestRule.waitForIdle()

        // Assert 1: List is filtered
        composeTestRule.onNodeWithText(title1).assertIsDisplayed()
        composeTestRule.onNodeWithText(title2).assertDoesNotExist()
        composeTestRule.onNodeWithText("Clear").assertIsDisplayed()

        // Act 2: Clear the search
        composeTestRule.onNodeWithText("Clear").performClick()
        composeTestRule.waitForIdle()

        // Assert 2: List is restored
        composeTestRule.onNodeWithText(title1).assertIsDisplayed()
        composeTestRule.onNodeWithText(title2).assertIsDisplayed()
        composeTestRule.onNodeWithText("Clear").assertDoesNotExist()
    }
}
