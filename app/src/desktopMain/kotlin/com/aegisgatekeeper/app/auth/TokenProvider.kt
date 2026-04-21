package com.aegisgatekeeper.app.auth

import com.aegisgatekeeper.app.GatekeeperStateManager

actual object TokenProvider {
    actual fun getToken(): String? = GatekeeperStateManager.state.value.jwtToken

    actual fun getSyncServerUrl(): String = GatekeeperStateManager.state.value.syncServerUrl
}
