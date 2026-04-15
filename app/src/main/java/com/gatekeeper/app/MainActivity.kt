package com.gatekeeper.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.gatekeeper.app.views.VaultReviewScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // UI Test Stability: Force the screen on and bypass the keyguard.
        // If the screen is off or locked, Compose will never perform a layout pass,
        // causing 'No compose hierarchies found' errors in tests.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }

        // Skip rendering the default UI if we are running instrumented tests.
        // This allows our tests to use MainActivity for its WakeLock properties
        // while calling composeTestRule.setContent { ... } to render specific isolated screens.
        val isRunningTest =
            try {
                Class.forName("androidx.test.espresso.Espresso")
                true
            } catch (e: ClassNotFoundException) {
                false
            }
        if (!isRunningTest) {
            setContent {
                MaterialTheme {
                    var selectedTab by remember { mutableIntStateOf(0) }

                    Column {
                        TabRow(selectedTabIndex = selectedTab) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                            ) {
                                Box(modifier = Modifier.padding(16.dp)) {
                                    Text("Lookup Vault")
                                }
                            }
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                            ) {
                                Box(modifier = Modifier.padding(16.dp)) {
                                    Text("Content Bank")
                                }
                            }
                        }

                        when (selectedTab) {
                            0 -> VaultReviewScreen()
                            1 -> ContentBankScreen()
                        }
                    }
                }
            }
        }
    }
}
