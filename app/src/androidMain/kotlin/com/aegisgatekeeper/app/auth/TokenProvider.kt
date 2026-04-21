package com.aegisgatekeeper.app.auth

import com.aegisgatekeeper.app.GatekeeperStateManager

actual object TokenProvider {
    actual fun getToken(): String? {
        // We must access the state directly on the thread that calls this.
        return GatekeeperStateManager.state.value.jwtToken
    }

    actual fun getSyncServerUrl(): String = GatekeeperStateManager.state.value.syncServerUrl
}
