package com.gatekeeper.app.views

import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

@Suppress("FunctionName")
@Composable
fun MovingCloseButton(onClose: () -> Unit) {
    val configuration = LocalConfiguration.current
    // Keep it safely within screen bounds (subtracting approx button width/height)
    val maxX = (configuration.screenWidthDp - 120).coerceAtLeast(0)
    val maxY = (configuration.screenHeightDp - 60).coerceAtLeast(0)

    val randomX by remember { mutableIntStateOf((0..maxX).random()) }
    val randomY by remember { mutableIntStateOf((0..maxY).random()) }

    OutlinedButton(
        onClick = onClose,
        modifier = Modifier.absoluteOffset(x = randomX.dp, y = randomY.dp),
    ) {
        Text("Give Up", color = Color.White.copy(alpha = 0.6f))
    }
}
