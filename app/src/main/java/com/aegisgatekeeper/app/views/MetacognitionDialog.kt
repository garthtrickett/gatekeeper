package com.aegisgatekeeper.app.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.Emotion
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.MetacognitionRequest
import com.aegisgatekeeper.app.domain.IndustrialButton

@Suppress("FunctionName")
@Composable
fun MetacognitionDialog(
    request: MetacognitionRequest,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Was this worth it?", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(32.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IndustrialButton(onClick = {
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.LogSessionMetacognition(
                                packageName = request.packageName,
                                durationMillis = request.durationMillis,
                                emotion = Emotion.HAPPY,
                                currentTimestamp = System.currentTimeMillis(),
                            )
                        )
                        onDismiss()
                    }, text = "Happy")
                    IndustrialButton(onClick = {
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.LogSessionMetacognition(
                                packageName = request.packageName,
                                durationMillis = request.durationMillis,
                                emotion = Emotion.ANXIOUS,
                                currentTimestamp = System.currentTimeMillis(),
                            )
                        )
                        onDismiss()
                    }, text = "Anxious")
                    IndustrialButton(onClick = {
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.LogSessionMetacognition(
                                packageName = request.packageName,
                                durationMillis = request.durationMillis,
                                emotion = Emotion.DRAINED,
                                currentTimestamp = System.currentTimeMillis(),
                            )
                        )
                        onDismiss()
                    }, text = "Drained")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            IndustrialButton(onClick = {
                GatekeeperStateManager.dispatch(
                    GatekeeperAction.LogSessionMetacognition(
                        packageName = request.packageName,
                        durationMillis = request.durationMillis,
                        emotion = Emotion.SKIPPED,
                        currentTimestamp = System.currentTimeMillis(),
                    )
                )
                onDismiss()
            }, text = "Skip", isWarning = true)
        }
    }
}
