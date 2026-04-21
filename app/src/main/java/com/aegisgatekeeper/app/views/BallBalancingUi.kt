package com.aegisgatekeeper.app.views

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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.services.GyroscopeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

data class Barrier(
    val y: Float,
    val gapCenterX: Float,
    val gapWidth: Float,
)

data class GauntletLevel(
    val barriers: List<Barrier>,
)

/** Pure logic to generate the Gauntlet maze difficulty based on past user behavior. */
fun generateGauntletLevel(bypassCount: Int): GauntletLevel {
    val numBarriers = (4 + bypassCount).coerceAtMost(8) // From 4 up to 8 barriers
    val gapWidth = (0.25f - (bypassCount * 0.02f)).coerceAtLeast(0.15f) // Gaps get smaller

    val barriers =
        List(numBarriers) { index ->
            val y = (index + 1f) / (numBarriers + 1f) // Space them evenly
            val gapCenterX = Random.nextFloat() * 0.6f + 0.2f // Random horizontal center
            Barrier(y, gapCenterX, gapWidth)
        }
    return GauntletLevel(barriers)
}

// --- Game Constants ---
private const val GRAVITY = 0.6f // Constant downward acceleration
private const val TILT_ACCELERATION = 1.5f // Horizontal acceleration from phone tilt
private const val BOUNCE_DAMPING = 0.5f // Energy retained when bouncing off walls
private const val BALL_RADIUS = 0.04f // Normalized radius (4% of canvas width)
private const val GAUNTLET_GOAL_SECONDS = 5f // Time required to balance to win
private const val HOLD_STEADY_GOAL_SECONDS = 10f
private const val SENSITIVITY = 1.5f
private const val WIND_STRENGTH = 0.8f // How strongly the wind pushes the ball

@Suppress("FunctionName")
@Composable
fun BallBalancingUi(
    title: String? = null,
    subtitle: String? = null,
    onSuccess: () -> Unit,
    onClose: () -> Unit,
) {
    val state by GatekeeperStateManager.state.collectAsState()

    if (state.activeFrictionGame == com.aegisgatekeeper.app.domain.FrictionGame.HOLD_STEADY) {
        HoldSteadyGame(
            title = title ?: "Hold Steady",
            subtitle = subtitle ?: "Keep the ball in the center circle.",
            onSuccess = onSuccess,
            onClose = onClose,
        )
    } else {
        GauntletGame(
            title = title ?: "The Gauntlet",
            subtitle = subtitle ?: "Guide the ball to the goal at the bottom.",
            onSuccess = onSuccess,
            onClose = onClose,
            bypassCount = state.analyticsBypasses,
        )
    }
}

@Suppress("FunctionName")
@Composable
private fun GauntletGame(
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onClose: () -> Unit,
    bypassCount: Int,
) {
    // --- State Management ---
    val gyroData by GyroscopeManager.gyroscopeData.collectAsState()
    var ballPosition by remember { mutableStateOf(Offset(0.5f, 0.1f)) }
    var ballVelocity by remember { mutableStateOf(Offset.Zero) }
    var progress by remember { mutableStateOf(0f) }

    // Generate the world structure once, with difficulty based on current state
    val level = remember(bypassCount) { generateGauntletLevel(bypassCount) }
    val barriers = level.barriers

    // --- Lifecycle Management for the Sensor ---
    DisposableEffect(Unit) {
        // When the UI appears, start listening to the gyroscope
        GyroscopeManager.startListening()
        // When the UI disappears (is disposed), stop listening to prevent battery drain
        onDispose { GyroscopeManager.stopListening() }
    }

    // --- The Game Loop ---
    LaunchedEffect(Unit) {
        var lastFrameTime = withFrameNanos { it }
        var hasWon = false

        while (isActive && !hasWon) {
            withFrameNanos { now ->
                val elapsedSeconds = (now - lastFrameTime) / 1_000_000_000f
                lastFrameTime = now

                // 1. Apply Accelerations
                // Android sensor roll is often opposite to physical tilt direction
                val ax = -gyroData.roll * TILT_ACCELERATION
                val ay = GRAVITY

                var vx = ballVelocity.x + (ax * elapsedSeconds)
                var vy = ballVelocity.y + (ay * elapsedSeconds)

                // Terminal velocity to prevent clipping through walls at high speeds
                vx = vx.coerceIn(-2f, 2f)
                vy = vy.coerceIn(-2f, 2f)

                // 2. Update Position
                var newX = ballPosition.x + (vx * elapsedSeconds)
                var newY = ballPosition.y + (vy * elapsedSeconds)

                // 3. Edge Collisions (Left & Right)
                if (newX < BALL_RADIUS) {
                    newX = BALL_RADIUS
                    vx = -vx * BOUNCE_DAMPING
                } else if (newX > 1f - BALL_RADIUS) {
                    newX = 1f - BALL_RADIUS
                    vx = -vx * BOUNCE_DAMPING
                }

                // 4. Edge Collisions (Top & Bottom)
                val ballRadiusY = 0.02f // Approximate aspect ratio compensation for Y axis
                if (newY < ballRadiusY) {
                    newY = ballRadiusY
                    vy = -vy * BOUNCE_DAMPING
                } else if (newY > 1f - ballRadiusY) {
                    newY = 1f - ballRadiusY
                    vy = -vy * BOUNCE_DAMPING
                }

                // 5. Barrier Collisions
                val barrierHalfThickness = 0.01f
                for (barrier in barriers) {
                    val barrierTop = barrier.y - barrierHalfThickness
                    val barrierBottom = barrier.y + barrierHalfThickness
                    val gapLeft = barrier.gapCenterX - barrier.gapWidth / 2f
                    val gapRight = barrier.gapCenterX + barrier.gapWidth / 2f

                    // Safe zone is strictly within the gap. If the ball is too wide, it hits the
                    // edges.
                    val inGap = newX - BALL_RADIUS > gapLeft && newX + BALL_RADIUS < gapRight

                    if (!inGap) {
                        if (vy > 0 &&
                            ballPosition.y + ballRadiusY <= barrierTop &&
                            newY + ballRadiusY >= barrierTop
                        ) {
                            // Check Top Collision (falling down into the barrier)
                            newY = barrierTop - ballRadiusY
                            vy = -vy * BOUNCE_DAMPING
                            vx *= 0.8f // Friction to stop it sliding too easily
                        } else if (vy < 0 &&
                            ballPosition.y - ballRadiusY >= barrierBottom &&
                            newY - ballRadiusY <= barrierBottom
                        ) {
                            // Check Bottom Collision (bouncing up into the barrier)
                            newY = barrierBottom + ballRadiusY
                            vy = -vy * BOUNCE_DAMPING
                            vx *= 0.8f // Friction
                        }
                    }
                }

                ballVelocity = Offset(vx, vy)
                ballPosition = Offset(newX, newY)

                // Win Condition: The ball must be in the bottom 5% of the screen for the timer to
                // count.
                if (ballPosition.y >= 0.95f) {
                    progress += elapsedSeconds
                } else {
                    // Reset progress if the ball leaves the goal area.
                    progress = 0f
                }

                if (progress >= GAUNTLET_GOAL_SECONDS) {
                    hasWon = true
                    onSuccess()
                }
            }
        }
    }

    // --- The UI ---
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f)),
    ) {
        // Muscle memory breaking close button
        MovingCloseButton(onClose = onClose)

        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(32.dp))

            // --- The Game Canvas ---
            val primaryColor = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.fillMaxSize(0.8f)) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val barrierThicknessPx = 12.dp.toPx()

                // Draw the goal area at the bottom
                drawRect(
                    color = primaryColor.copy(alpha = 0.1f),
                    topLeft = Offset(0f, canvasHeight * 0.95f),
                    size =
                        androidx.compose.ui.geometry
                            .Size(canvasWidth, canvasHeight * 0.1f),
                )

                // Draw the barriers
                barriers.forEach { barrier ->
                    val yPx = barrier.y * canvasHeight
                    val gapLeftPx = (barrier.gapCenterX - barrier.gapWidth / 2f) * canvasWidth
                    val gapRightPx = (barrier.gapCenterX + barrier.gapWidth / 2f) * canvasWidth

                    drawLine(
                        color = Color.LightGray,
                        start = Offset(0f, yPx),
                        end = Offset(gapLeftPx, yPx),
                        strokeWidth = barrierThicknessPx,
                        cap = StrokeCap.Round,
                    )

                    drawLine(
                        color = Color.LightGray,
                        start = Offset(gapRightPx, yPx),
                        end = Offset(canvasWidth, yPx),
                        strokeWidth = barrierThicknessPx,
                        cap = StrokeCap.Round,
                    )
                }

                // Draw the ball
                val chromeBrush =
                    Brush.radialGradient(
                        colors = listOf(Color.White, primaryColor, Color.Black),
                        center =
                            Offset(
                                (ballPosition.x * canvasWidth) - (canvasWidth * BALL_RADIUS * 0.5f),
                                (ballPosition.y * canvasHeight) - (canvasHeight * 0.02f * 0.5f),
                            ),
                        radius = canvasWidth * BALL_RADIUS * 1.5f,
                    )
                drawCircle(
                    brush = chromeBrush,
                    radius = canvasWidth * BALL_RADIUS,
                    center =
                        Offset(ballPosition.x * canvasWidth, ballPosition.y * canvasHeight),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- Progress Bar ---
            LinearProgressIndicator(
                progress = { progress / GAUNTLET_GOAL_SECONDS },
                modifier = Modifier.fillMaxWidth(0.7f),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun HoldSteadyGame(
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onClose: () -> Unit,
) {
    // --- State Management ---
    val gyroData by GyroscopeManager.gyroscopeData.collectAsState()
    var ballPosition by remember { mutableStateOf(Offset.Zero) }
    var progress by remember { mutableStateOf(0f) }
    var windForce by remember { mutableStateOf(Offset.Zero) }

    // --- Lifecycle Management for the Sensor ---
    DisposableEffect(Unit) {
        // When the UI appears, start listening to the gyroscope
        GyroscopeManager.startListening()
        // When the UI disappears (is disposed), stop listening to prevent battery drain
        onDispose { GyroscopeManager.stopListening() }
    }

    // --- Wind Simulation ---
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(Random.nextLong(2000, 4000)) // Change wind every 2-4 seconds
            if (isActive) {
                windForce =
                    Offset(
                        x = (Random.nextFloat() - 0.5f) * WIND_STRENGTH,
                        y = (Random.nextFloat() - 0.5f) * WIND_STRENGTH,
                    )
            }
        }
    }

    // --- The Game Loop ---
    LaunchedEffect(Unit) {
        var lastFrameTime = withFrameNanos { it }
        var hasWon = false

        // Initialize ball to the center
        ballPosition = Offset(0.5f, 0.5f)

        while (isActive && !hasWon) {
            withFrameNanos { now ->
                val elapsedSeconds = (now - lastFrameTime) / 1_000_000_000f
                lastFrameTime = now

                // Calculate new ball position based on gravity/accelerometer tilt and wind
                val windEffectX = windForce.x * elapsedSeconds
                val windEffectY = windForce.y * elapsedSeconds

                // Android sensor axes measure the restoring force, which is opposite to the tilt
                // direction.
                val newX =
                    (
                        ballPosition.x - gyroData.roll * SENSITIVITY * elapsedSeconds +
                            windEffectX
                    ).coerceIn(0f, 1f)
                val newY =
                    (
                        ballPosition.y +
                            gyroData.pitch * SENSITIVITY * elapsedSeconds +
                            windEffectY
                    ).coerceIn(0f, 1f)
                ballPosition = Offset(newX, newY)

                // Check distance from center (0.5, 0.5)
                val distance = sqrt((newX - 0.5f).pow(2) + (newY - 0.5f).pow(2))

                // Target is a circle with radius 0.3 (30% of the screen width)
                if (distance < 0.3f) {
                    progress += elapsedSeconds
                } else {
                    // If the ball leaves the target, reset progress
                    progress = 0f
                }

                if (progress >= HOLD_STEADY_GOAL_SECONDS) {
                    hasWon = true
                    onSuccess()
                }
            }
        }
    }

    // --- The UI ---
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f)),
    ) {
        // Muscle memory breaking close button
        MovingCloseButton(onClose = onClose)

        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(32.dp))

            // --- The Game Canvas ---
            val surfaceColor = MaterialTheme.colorScheme.surface
            val primaryColor = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.fillMaxSize(0.8f)) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // Draw the milled recess effect
                drawCircle(
                    color = Color.Black,
                    radius = canvasWidth * 0.3f,
                    center = center,
                    style = Stroke(width = 8.dp.toPx()),
                )
                // Draw the target circle base
                drawCircle(
                    color = surfaceColor,
                    radius = canvasWidth * 0.3f,
                    center = center,
                )

                // Draw the ball
                val chromeBrush =
                    Brush.radialGradient(
                        colors = listOf(Color.White, primaryColor, Color.Black),
                        center =
                            Offset(
                                (ballPosition.x * canvasWidth) - (canvasWidth * 0.04f * 0.5f),
                                (ballPosition.y * canvasHeight) - (canvasHeight * 0.04f * 0.5f),
                            ),
                        radius = canvasWidth * 0.04f * 1.5f,
                    )
                drawCircle(
                    brush = chromeBrush,
                    radius = canvasWidth * 0.04f, // Ball is smaller than target
                    center =
                        Offset(ballPosition.x * canvasWidth, ballPosition.y * canvasHeight),
                )

                // Draw wind indicator
                if (windForce != Offset.Zero) {
                    val arrowStart = Offset(size.width * 0.1f, size.height * 0.1f)
                    val windVector =
                        windForce.copy(
                            x = windForce.x * canvasWidth * 0.2f,
                            y = windForce.y * canvasHeight * 0.2f,
                        )
                    val arrowEnd = arrowStart + windVector

                    drawLine(
                        color = Color.White.copy(alpha = 0.5f),
                        start = arrowStart,
                        end = arrowEnd,
                        strokeWidth = 12f,
                        cap = StrokeCap.Round,
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.5f),
                        radius = 8f,
                        center = arrowStart,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- Progress Bar ---
            LinearProgressIndicator(
                progress = { progress / HOLD_STEADY_GOAL_SECONDS },
                modifier = Modifier.fillMaxWidth(0.7f),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
