package com.aegisgatekeeper.app.views

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.IndustrialButton
import com.aegisgatekeeper.app.domain.IndustrialTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(
    val packageName: String,
    val name: String,
    val icon: Drawable?,
    val groupNames: List<String> = emptyList(),
)

@Suppress("FunctionName")
@Composable
fun MissionControlScreen() {
    val state by GatekeeperStateManager.state.collectAsState()
    val context = LocalContext.current
    val pm = context.packageManager

    var showAddChoice by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showWebsiteDialog by remember { mutableStateOf(false) }
    var pinnedAppsInfo by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    LaunchedEffect(state.missionControlApps, state.appGroups) {
        withContext(Dispatchers.IO) {
            val loaded =
                state.missionControlApps.mapNotNull { pkg ->
                    try {
                        val appInfo = pm.getApplicationInfo(pkg, 0)
                        val name = pm.getApplicationLabel(appInfo).toString()
                        val icon = pm.getApplicationIcon(appInfo)
                        val groups = state.appGroups.filter { it.apps.contains(pkg) }.map { it.name }
                        AppInfo(pkg, name, icon, groups)
                    } catch (e: Exception) {
                        null
                    }
                }
            pinnedAppsInfo = loaded
        }
    }

    if (showAddChoice) {
        AddChoiceDialog(
            onDismiss = { showAddChoice = false },
            onAddApp = {
                showAddChoice = false
                showAppPicker = true
            },
            onAddWebsite = {
                showAddChoice = false
                showWebsiteDialog = true
            },
        )
    }

    if (showAppPicker) {
        AppPickerDialog(
            currentPinned = state.missionControlApps.toSet(),
            onDismiss = { showAppPicker = false },
            onSave = { newPinned ->
                GatekeeperStateManager.dispatch(GatekeeperAction.UpdateMissionControlApps(newPinned.toList()))
                showAppPicker = false
            },
        )
    }

    if (showWebsiteDialog) {
        AddWebsiteDialog(
            onDismiss = { showWebsiteDialog = false },
            onSave = { label, url ->
                GatekeeperStateManager.dispatch(
                    GatekeeperAction.AddPinnedWebsite(
                        id =
                            java.util.UUID
                                .randomUUID()
                                .toString(),
                        label = label,
                        url = url,
                    ),
                )
                showWebsiteDialog = false
            },
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("Mission Control", style = MaterialTheme.typography.headlineLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Your intentional safe room. Launch essential tools here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(24.dp))

                // --- Surgical Utilities ---
                Text(
                    "Surgical Utilities",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IndustrialButton(
                        onClick = {
                            GatekeeperStateManager.dispatch(
                                GatekeeperAction.OpenSurgicalFacebook("https://m.facebook.com/groups/"),
                            )
                        },
                        text = "FB Groups",
                        modifier = Modifier.weight(1f),
                    )
                    IndustrialButton(
                        onClick = {
                            GatekeeperStateManager.dispatch(
                                GatekeeperAction.OpenSurgicalFacebook("https://m.facebook.com/events/"),
                            )
                        },
                        text = "FB Events",
                        modifier = Modifier.weight(1f),
                    )
                    IndustrialButton(
                        onClick = {
                            GatekeeperStateManager.dispatch(
                                GatekeeperAction.OpenSurgicalFacebook("https://m.facebook.com/search/"),
                            )
                        },
                        text = "FB Search",
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (pinnedAppsInfo.isEmpty() && state.missionControlWebsites.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No shortcuts pinned. Add some essentials.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val safeApps = pinnedAppsInfo.filter { it.groupNames.isEmpty() }
                    val restrictedApps = pinnedAppsInfo.filter { it.groupNames.isNotEmpty() }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (safeApps.isNotEmpty()) {
                            item(span = {
                                androidx.compose.foundation.lazy.grid
                                    .GridItemSpan(maxLineSpan)
                            }) {
                                Text(
                                    "Safe Apps",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                                )
                            }
                            items(safeApps, key = { it.packageName }) { app ->
                                AppDockButton(app = app) {
                                    val intent = pm.getLaunchIntentForPackage(app.packageName)
                                    if (intent != null) {
                                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    }
                                }
                            }
                        }

                        if (restrictedApps.isNotEmpty()) {
                            item(span = {
                                androidx.compose.foundation.lazy.grid
                                    .GridItemSpan(maxLineSpan)
                            }) {
                                Text(
                                    "Restricted Apps",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                                )
                            }
                            items(restrictedApps, key = { it.packageName }) { app ->
                                AppDockButton(app = app) {
                                    val intent = pm.getLaunchIntentForPackage(app.packageName)
                                    if (intent != null) {
                                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    }
                                }
                            }
                        }

                        if (state.missionControlWebsites.isNotEmpty()) {
                            item(span = {
                                androidx.compose.foundation.lazy.grid
                                    .GridItemSpan(maxLineSpan)
                            }) {
                                Text(
                                    "Pinned Websites",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                                )
                            }
                            items(state.missionControlWebsites, key = { it.id }) { website ->
                                WebsiteDockButton(
                                    website = website,
                                    onClick = {
                                        GatekeeperStateManager.dispatch(GatekeeperAction.OpenPinnedWebsite(website.url))
                                    },
                                    onDelete = {
                                        GatekeeperStateManager.dispatch(GatekeeperAction.RemovePinnedWebsite(website.id))
                                    },
                                )
                            }
                        }
                    }
                }
            }
            IndustrialButton(
                onClick = { showAddChoice = true },
                text = "+",
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

@Suppress("FunctionName")
@Composable
fun AppDockButton(
    app: AppInfo,
    onClick: () -> Unit,
) {
    val grayscaleMatrix = remember { ColorMatrix().apply { setToSaturation(0f) } }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.85f)
                .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            app.icon?.let { drawable ->
                val bitmap =
                    remember(drawable) {
                        val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 120
                        val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 120
                        drawable.toBitmap(width = w, height = h).asImageBitmap()
                    }
                Image(
                    bitmap = bitmap,
                    contentDescription = app.name,
                    modifier = Modifier.size(48.dp),
                    colorFilter = ColorFilter.colorMatrix(grayscaleMatrix),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = app.name,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (app.groupNames.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = app.groupNames.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
fun WebsiteDockButton(
    website: com.aegisgatekeeper.app.domain.PinnedWebsite,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.85f)
                .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "🌐",
                    fontSize = 36.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = website.label,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "🗑️",
                fontSize = 14.sp,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clickable { onDelete() },
            )
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun AddChoiceDialog(
    onDismiss: () -> Unit,
    onAddApp: () -> Unit,
    onAddWebsite: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Add to Mission Control", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(24.dp))
                IndustrialButton(
                    onClick = onAddApp,
                    text = "Add an App",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                IndustrialButton(
                    onClick = onAddWebsite,
                    text = "Add a Website",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun AddWebsiteDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Pin a Website", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                IndustrialTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(16.dp))
                IndustrialTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL (e.g. https://...)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IndustrialButton(onClick = onDismiss, text = "Cancel", isWarning = true)
                    Spacer(modifier = Modifier.width(8.dp))
                    IndustrialButton(
                        onClick = { onSave(label, url) },
                        text = "Save",
                        enabled = label.isNotBlank() && url.isNotBlank() && url.startsWith("http"),
                    )
                }
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
fun AppPickerDialog(
    currentPinned: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    val pm = context.packageManager
    var selectedApps by remember { mutableStateOf(currentPinned) }
    var searchQuery by remember { mutableStateOf("") }

    val installedApps =
        remember {
            pm
                .getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != context.packageName }
                .sortedBy { pm.getApplicationLabel(it).toString() }
        }

    val filteredApps =
        remember(searchQuery, installedApps) {
            if (searchQuery.isBlank()) {
                installedApps
            } else {
                installedApps.filter {
                    pm.getApplicationLabel(it).toString().contains(searchQuery, ignoreCase = true)
                }
            }
        }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(500.dp)
                    .padding(16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Pin Essential Apps", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                IndustrialTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search apps...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedApps =
                                            if (selectedApps.contains(app.packageName)) {
                                                selectedApps - app.packageName
                                            } else {
                                                selectedApps + app.packageName
                                            }
                                    }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selectedApps.contains(app.packageName),
                                onCheckedChange = null,
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(pm.getApplicationLabel(app).toString())
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IndustrialButton(onClick = onDismiss, text = "Cancel", isWarning = true)
                    Spacer(modifier = Modifier.width(8.dp))
                    IndustrialButton(onClick = { onSave(selectedApps) }, text = "Save Dock")
                }
            }
        }
    }
}
