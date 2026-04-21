package com.aegisgatekeeper.app.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.IndustrialButton
import com.aegisgatekeeper.app.services.PermissionIntents

@Suppress("FunctionName")
@Composable
fun PermissionsOnboardingScreen() {
    val state by GatekeeperStateManager.state.collectAsState()
    val context = LocalContext.current
    val pagerState = rememberPagerState { 4 }

    // Auto-advance logic driven purely by the SAM loop state
    LaunchedEffect(
        state.hasOverlayPermission,
        state.hasUsageAccessPermission,
        state.hasAccessibilityPermission,
        state.isBatteryOptimizationDisabled,
    ) {
        if (!state.hasOverlayPermission) {
            pagerState.animateScrollToPage(0)
        } else if (!state.hasUsageAccessPermission) {
            pagerState.animateScrollToPage(1)
        } else if (!state.hasAccessibilityPermission) {
            pagerState.animateScrollToPage(2)
        } else if (!state.isBatteryOptimizationDisabled) {
            pagerState.animateScrollToPage(3)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false, // Force them to grant the permission to advance
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> {
                    PermissionPage(
                        stepNumber = 1,
                        title = "The Iron Gate",
                        description =
                            "To block distractions, Gatekeeper must be able to draw an " +
                                "impenetrable wall over other apps.\n\nThis is the foundation of the moat.",
                        buttonText = "Grant Overlay Permission",
                        onClick = { context.startActivity(PermissionIntents.getOverlayIntent(context)) },
                    )
                }

                1 -> {
                    PermissionPage(
                        stepNumber = 2,
                        title = "App Tracking",
                        description =
                            "To enforce time limits, Gatekeeper needs to track your app usage.\n\nData is strictly local and never leaves your device.",
                        buttonText = "Grant Usage Access",
                        onClick = { context.startActivity(PermissionIntents.getUsageAccessIntent()) },
                    )
                }

                2 -> {
                    PermissionPage(
                        stepNumber = 3,
                        title = "Layer Omega",
                        description =
                            "The Sovereign Engine.\n\nThe zero-latency hard block. This " +
                                "intercepts the app before your brain gets the dopamine hit. Requires " +
                                "Accessibility Service.",
                        buttonText = "Enable Accessibility",
                        onClick = { context.startActivity(PermissionIntents.getAccessibilityIntent()) },
                    )
                }

                3 -> {
                    BatteryOptimizationPage(
                        onClick = { context.startActivity(PermissionIntents.getBatteryOptimizationIntent(context)) },
                    )
                }
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun BatteryOptimizationPage(onClick: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "STEP 4 OF 4",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "The Final Boss",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text =
                "Your phone's OS will try to assassinate Gatekeeper in the background to save " +
                    "battery.\n\nDepending on your device (Samsung, Xiaomi, Pixel), you must select " +
                    "'Unrestricted' or 'No Restrictions' in the next menu.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        androidx.compose.material3.Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                androidx.compose.material3.CardDefaults
                    .cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "⚠️ CRITICAL",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text =
                        "If you skip this, Layer Alpha and Layer Omega will be silently killed by " +
                            "the OS within 24 hours.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        IndustrialButton(
            onClick = onClick,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            text = "Disable Battery Optimization",
        )
    }
}

@Suppress("FunctionName")
@Composable
private fun PermissionPage(
    stepNumber: Int,
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "STEP $stepNumber OF 4",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(48.dp))

        IndustrialButton(
            onClick = onClick,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            text = buttonText,
        )
    }
}
