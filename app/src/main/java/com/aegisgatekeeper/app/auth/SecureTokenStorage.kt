package com.aegisgatekeeper.app.auth

import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.aegisgatekeeper.app.App

object SecureTokenStorage {
    private const val PREFS_FILE_NAME = "gatekeeper_secure_prefs"
    private const val JWT_TOKEN_KEY = "jwt_token"

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val sharedPreferences =
        EncryptedSharedPreferences.create(
            PREFS_FILE_NAME,
            masterKeyAlias,
            App.instance.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    fun saveToken(token: String) {
        with(sharedPreferences.edit()) {
            putString(JWT_TOKEN_KEY, token)
            apply()
        }
    }

    fun getToken(): String? = sharedPreferences.getString(JWT_TOKEN_KEY, null)

    fun clearToken() {
        with(sharedPreferences.edit()) {
            remove(JWT_TOKEN_KEY)
            apply()
        }
    }
}
