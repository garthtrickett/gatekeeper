package com.aegisgatekeeper.app.auth

// Using an object to avoid instantiation complexities in KMP
expect object TokenProvider {
    fun getToken(): String?

    fun getSyncServerUrl(): String
}
