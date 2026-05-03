package com.aegisgatekeeper.app.auth

import com.aegisgatekeeper.app.GatekeeperStateManager
import me.tatarka.inject.annotations.Inject

@Inject
class AndroidTokenProvider : TokenProvider {
    override fun getToken(): String? {
        // We must access the state directly on the thread that calls this.
        return GatekeeperStateManager.state.value.sync.jwtToken
    }

    override fun getSyncServerUrl(): String = GatekeeperStateManager.state.value.sync.syncServerUrl
}
