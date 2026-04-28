package com.aegisgatekeeper.app.auth

interface TokenProvider {
    fun getToken(): String?

    fun getSyncServerUrl(): String
}
