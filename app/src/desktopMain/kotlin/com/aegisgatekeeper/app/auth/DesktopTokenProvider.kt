package com.aegisgatekeeper.app.auth

import com.aegisgatekeeper.app.GatekeeperStateManager
import me.tatarka.inject.annotations.Inject

@Inject
class DesktopTokenProvider : TokenProvider {
    override fun getToken(): String? = GatekeeperStateManager.state.value.sync.jwtToken

    override fun getSyncServerUrl(): String = GatekeeperStateManager.state.value.sync.syncServerUrl
}
