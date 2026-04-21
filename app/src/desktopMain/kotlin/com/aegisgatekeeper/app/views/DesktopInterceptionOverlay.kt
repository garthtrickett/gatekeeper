package com.aegisgatekeeper.app.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.IndustrialButton

@Suppress("FunctionName")
@Composable
fun DesktopInterceptionOverlay(interceptedApp: String) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Take a breath.",
                color = Color.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "You are about to open $interceptedApp.",
                color = Color.LightGray,
                fontSize = 24.sp,
            )
            Spacer(modifier = Modifier.height(64.dp))

            // In a complete implementation, this would trigger the Ball Balancing Game.
            // For the desktop MVP, we offer a strict bypass.
            IndustrialButton(
                onClick = {
                    GatekeeperStateManager.dispatch(GatekeeperAction.DismissOverlay)
                },
                modifier = Modifier.height(56.dp).padding(horizontal = 32.dp),
                text = "Acknowledge & Bypass",
            )
        }
    }
}
