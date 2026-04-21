package com.aegisgatekeeper.app.views

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aegisgatekeeper.app.GatekeeperStateManager
import com.aegisgatekeeper.app.domain.AppGroup
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.views.groups.AddGroupScreen
import com.aegisgatekeeper.app.views.groups.AppGroupsListScreen
import com.aegisgatekeeper.app.views.groups.GroupDetailScreen
import java.util.UUID

@Suppress("FunctionName")
@Composable
fun AppGroupsScreen() {
    val state by GatekeeperStateManager.state.collectAsState()
    var selectedGroup by remember { mutableStateOf<AppGroup?>(null) }
    var showAddGroup by remember { mutableStateOf(false) }

    if (showAddGroup) {
        AddGroupScreen(
            onSave = { name, apps ->
                GatekeeperStateManager.dispatch(GatekeeperAction.CreateAppGroup(UUID.randomUUID().toString(), name, apps))
                showAddGroup = false
            },
            onCancel = { showAddGroup = false },
        )
        return
    }

    if (selectedGroup != null) {
        val currentGroup = state.appGroups.find { it.id == selectedGroup!!.id }
        if (currentGroup == null) {
            selectedGroup = null
        } else {
            GroupDetailScreen(
                group = currentGroup,
                onBack = { selectedGroup = null },
            )
            return
        }
    }

    AppGroupsListScreen(
        state = state,
        onGroupSelected = { selectedGroup = it },
        onAddGroupClick = { showAddGroup = true },
    )
}
