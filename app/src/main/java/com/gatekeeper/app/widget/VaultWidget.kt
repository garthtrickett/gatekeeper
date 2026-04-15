package com.gatekeeper.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.gatekeeper.app.VaultCaptureActivity

class VaultWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            Box(
                modifier = 
                    GlanceModifier
                        .fillMaxSize()
                        .background(Color(0xFF1E1E1E))
                        .padding(12.dp)
                        .clickable(actionStartActivity(Intent(context, VaultCaptureActivity::class.java))),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "🔍 Save to Lookup Vault...",
                    style = TextStyle(color = ColorProvider(Color.White)),
                )
            }
        }
    }
}
