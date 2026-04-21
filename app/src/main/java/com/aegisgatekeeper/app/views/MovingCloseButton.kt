package com.aegisgatekeeper.app.views

import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.aegisgatekeeper.app.domain.IndustrialButton

@Suppress("FunctionName")
@Composable
fun MovingCloseButton(onClose: () -> Unit) {
    val configuration = LocalConfiguration.current
    val padding = 32 // Safe margin from screen edges

    // Button dimensions (approximate)
    val btnWidth = 120
    val btnHeight = 60

    val maxX = (configuration.screenWidthDp - btnWidth - padding).coerceAtLeast(padding)
    val maxY = (configuration.screenHeightDp - btnHeight - padding).coerceAtLeast(padding)

    // Randomly pick one of 4 corners: 0=TopLeft, 1=TopRight, 2=BottomLeft, 3=BottomRight
    val cornerIndex by remember { mutableIntStateOf((0..3).random()) }

    val (offsetX, offsetY) =
        when (cornerIndex) {
            0 -> padding to padding
            1 -> maxX to padding
            2 -> padding to maxY
            else -> maxX to maxY
        }

    IndustrialButton(
        onClick = onClose,
        modifier = Modifier.absoluteOffset(x = offsetX.dp, y = offsetY.dp),
        text = "Give Up",
        isWarning = true,
    )
}
