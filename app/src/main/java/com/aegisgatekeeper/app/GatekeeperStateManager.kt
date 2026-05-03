package com.aegisgatekeeper.app

import android.content.Context
import android.util.Log
import com.aegisgatekeeper.app.auth.SecureTokenStorage
import com.aegisgatekeeper.app.db.DatabaseManager
import com.aegisgatekeeper.app.domain.ContentItem
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperState
import com.aegisgatekeeper.app.domain.ScheduledMessage
import com.aegisgatekeeper.app.domain.SessionLog
import com.aegisgatekeeper.app.domain.VaultItem
import com.aegisgatekeeper.app.domain.reduce
import com.aegisgatekeeper.app.effects.handleDatabaseEffects
import com.aegisgatekeeper.app.effects.handleIntegrationEffects
import com.aegisgatekeeper.app.effects.handleMediaAndSystemEffects
import com.aegisgatekeeper.app.effects.handleSyncAndAuthEffects
import com.aegisgatekeeper.app.widget.VaultWidget
import com.aegisgatekeeper.app.widget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object DeletedGatekeeperStateManager

object DeletedGatekeeperStateManager2
package com.aegisgatekeeper.app

object DeletedGatekeeperStateManager
object DeletedGatekeeperStateManager2

