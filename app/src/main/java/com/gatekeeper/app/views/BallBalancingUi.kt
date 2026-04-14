package com.gatekeeper.app.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gatekeeper.app.GatekeeperStateManager
import com.gatekeeper.app.domain.GatekeeperAction
import com.gatekeeper.app.services.GyroscopeManager
import kotlinx.coroutines.isActive
import kotlin.math.pow
import kotlin.math.sqrt

// --- Game Constants ---
private const val GOAL_SECONDS = 15f
private const val SENSITIVITY = 30f // Higher is faster

@Suppress("FunctionName")
@Composable
fun BallBalancingUi(interceptedPackage: String) {
    // --- State Management ---
    val gyroData by GyroscopeManager.gyroscopeData.collectAsState()
    var ballPosition by remember { mutableStateOf(Offset.Zero) }
    var progress by remember { mutableStateOf(0f) }

    // --- Lifecycle Management for the Sensor ---
    DisposableEffect(Unit) {
        // When the UI appears, start listening to the gyroscope
        GyroscopeManager.startListening()
        // When the UI disappears (is disposed), stop listening to prevent battery drain
        onDispose {
            GyroscopeManager.stopListening()
        }
    }

    // --- The Game Loop ---
    LaunchedEffect(Unit) {
        var lastFrameTime = withFrameNanos { it }

        // Initialize ball to the center
        ballPosition = Offset(0.5f, 0.5f)

        while (isActive) {
            withFrameNanos { now ->
                val elapsedSeconds = (now - lastFrameTime) / 1_000_000_000f
                lastFrameTime = now

                // Calculate new ball position based on gyroscope tilt
                // Note: We use relative positions (0.0 to 1.0) to be screen-size independent
                val newX = (ballPosition.x - gyroData.roll * SENSITIVITY * elapsedSeconds).coerceIn(0f, 1f)
                val newY = (ballPosition.y + gyroData.pitch * SENSITIVITY * elapsedSeconds).coerceIn(0f, 1f)
                ballPosition = Offset(newX, newY)

                // Check distance from center (0.5, 0.5)
                val distance = sqrt((newX - 0.5f).pow(2) + (newY - 0.5f).pow(2))

                // Target is a circle with radius 0.1 (10% of the screen width)
                if (distance < 0.1f) {
                    progress += elapsedSeconds
                } else {
                    // If the ball leaves the target, reset progress
                    progress = 0f
                }

                if (progress >= GOAL_SECONDS) {
                    // You win! Dispatch the action.
                    GatekeeperStateManager.dispatch(
                        GatekeeperAction.FrictionCompleted(
                            packageName = interceptedPackage,
                            currentTimestamp = System.currentTimeMillis(),
                        ),
                    )
                }
            }
        }
    }

    // --- The UI ---
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Hold Steady",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Keep the ball in the target for ${GOAL_SECONDS.toInt()} seconds.",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(32.dp))

        // --- The Game Canvas ---
        Canvas(modifier = Modifier.fillMaxSize(0.8f)) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Draw the target circle
            drawCircle(
                color = Color.DarkGray,
                radius = canvasWidth * 0.1f,
                center = center,
            )

            // Draw the ball
            drawCircle(
                color = Color.Cyan,
                radius = canvasWidth * 0.04f, // Ball is smaller than target
                center = Offset(ballPosition.x * canvasWidth, ballPosition.y * canvasHeight),
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- Progress Bar ---
        LinearProgressIndicator(
            progress = { progress / GOAL_SECONDS },
            modifier = Modifier.fillMaxWidth(0.7f),
            color = Color.Cyan,
        )
        }
    }
}
