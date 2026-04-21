package com.aegisgatekeeper.app.views

import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aegisgatekeeper.app.domain.IndustrialButton

@Suppress("FunctionName")
@Composable
fun MovingCloseButton(onClose: () -> Unit) {
    val padding = 32 // Safe margin from screen edges

    val offsetX = padding
    val offsetY = padding

    IndustrialButton(
        onClick = onClose,
        modifier = Modifier.absoluteOffset(x = offsetX.dp, y = offsetY.dp),
        text = "Give Up",
        isWarning = true,
    )
}
