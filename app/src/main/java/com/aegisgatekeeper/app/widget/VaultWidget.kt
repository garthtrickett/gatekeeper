package com.aegisgatekeeper.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.aegisgatekeeper.app.MainActivity
import com.aegisgatekeeper.app.VaultCaptureActivity
import com.aegisgatekeeper.app.db.DatabaseManager
import com.aegisgatekeeper.app.domain.ContentType
import com.aegisgatekeeper.app.domain.isDeepWorkHours
import java.time.LocalTime

suspend fun GlanceAppWidget.updateAll(context: Context) {
    GlanceAppWidgetManager(context).getGlanceIds(this::class.java).forEach { glanceId ->
        update(context, glanceId)
    }
}

class UnmaskAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        androidx.glance.appwidget.state.updateAppWidgetState(context, glanceId) { prefs ->
            prefs[booleanPreferencesKey("isUnmasked")] = true
        }
        VaultWidget().update(context, glanceId)
    }
}

class VaultWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
            val isUnmasked = prefs[booleanPreferencesKey("isUnmasked")] ?: false

            val appSettings =
                DatabaseManager.db.appSettingsQueries
                    .getSettings()
                    .executeAsOneOrNull()
            val isProTier = appSettings?.isProTier ?: false

            val displayItems =
                DatabaseManager.db.intentionalSlotQueries
                    .selectAll()
                    .executeAsList()
            val totalCount = displayItems.size.toLong()

            Box(
                modifier =
                    GlanceModifier
                        .fillMaxSize()
                        .background(Color(0xFF1E1E1E))
                        .padding(12.dp),
            ) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Sovereignty Dashboard",
                            style = TextStyle(color = ColorProvider(Color.Gray), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        Text(
                            text = "🔍 Vault",
                            style = TextStyle(color = ColorProvider(Color.Cyan), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            modifier = GlanceModifier.clickable(actionStartActivity(Intent(context, VaultCaptureActivity::class.java))),
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(12.dp))

                    if (displayItems.isEmpty()) {
                        Text(
                            text = "Dashboard is empty. Open app to assign slots.",
                            style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp),
                        )
                    } else {
                        if (!isUnmasked) {
                            Text(
                                text = "████████████████\nContent Hidden",
                                style = TextStyle(color = ColorProvider(Color.DarkGray), fontSize = 16.sp, fontWeight = FontWeight.Bold),
                            )
                            Spacer(modifier = GlanceModifier.height(12.dp))
                            Button(
                                text = "Show Intent",
                                onClick = actionRunCallback<UnmaskAction>(),
                            )
                        } else {
                            LazyColumn(modifier = GlanceModifier.defaultWeight()) {
                                items(displayItems) {
                                    Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 12.dp)) {
                                        Text(
                                            text = "Slot ${it.slotIndex + 1}: ${it.title}",
                                            style =
                                                TextStyle(
                                                    color = ColorProvider(Color.White),
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                ),
                                            maxLines = 2,
                                        )
                                        Spacer(modifier = GlanceModifier.height(4.dp))
                                        Text(
                                            text = "${it.type.name} • ${it.source.name}",
                                            style = TextStyle(color = ColorProvider(Color.Gray), fontSize = 12.sp),
                                        )
                                        Spacer(modifier = GlanceModifier.height(8.dp))

                                        val intent =
                                            Intent(context, MainActivity::class.java).apply {
                                                if (it.type == ContentType.VIDEO) {
                                                    putExtra("OPEN_CLEAN_PLAYER_VIDEO_ID", it.videoId)
                                                } else if (it.type == ContentType.AUDIO) {
                                                    putExtra("OPEN_CLEAN_AUDIO_URL", it.videoId)
                                                }
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                            }

                                        Button(
                                            text = "Open",
                                            onClick = actionStartActivity(intent),
                                        )
                                    }
                                }

                                if (!isProTier) {
                                    item {
                                        Text(
                                            text = "🔒 Pro Required for Dashboard.",
                                            style =
                                                TextStyle(
                                                    color = ColorProvider(Color.Cyan),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                ),
                                            modifier = GlanceModifier.padding(top = 8.dp, bottom = 8.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
