package com.example.myapplication.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(
    context: Context,
    val defaultClientId: String,
    val defaultOauthToken: String = "",
    val defaultUserId: String = ""
) {
    private val preferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _clientId = MutableStateFlow(
        preferences.getString(KEY_CLIENT_ID, null)?.takeIf { it.isNotBlank() } ?: defaultClientId
    )
    val clientId = _clientId.asStateFlow()

    private val _oauthToken = MutableStateFlow(readOauthToken())
    val oauthToken = _oauthToken.asStateFlow()

    private val _userId = MutableStateFlow(readUserId())
    val userId = _userId.asStateFlow()

    fun oauthTokenValue(): String = _oauthToken.value
    fun userIdValue(): String = _userId.value

    fun saveClientId(value: String) {
        val cleaned = value.trim()
        if (cleaned.isBlank()) return

        _clientId.value = cleaned
        preferences.edit().putString(KEY_CLIENT_ID, cleaned).apply()
    }

    fun resetClientId() {
        _clientId.value = defaultClientId
        preferences.edit().remove(KEY_CLIENT_ID).apply()
    }

    fun saveOauthToken(value: String) {
        val cleaned = normalizeOauthToken(value)
        _oauthToken.value = cleaned
        preferences.edit().putString(KEY_OAUTH_TOKEN, cleaned).apply()
    }

    fun resetOauthToken() {
        _oauthToken.value = defaultOauthToken
        preferences.edit().remove(KEY_OAUTH_TOKEN).apply()
    }

    fun saveUserId(value: String) {
        val cleaned = value.trim()
        _userId.value = cleaned
        preferences.edit().putString(KEY_USER_ID, cleaned).apply()
    }

    fun resetUserId() {
        _userId.value = defaultUserId
        preferences.edit().remove(KEY_USER_ID).apply()
    }

    private fun readOauthToken(): String {
        val stored = preferences.getString(KEY_OAUTH_TOKEN, null)?.takeIf { it.isNotBlank() }
        return stored ?: defaultOauthToken
    }

    private fun readUserId(): String {
        val stored = preferences.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() }
        return stored ?: defaultUserId
    }

    private fun normalizeOauthToken(value: String): String =
        value.trim().removePrefix("OAuth ").trim()

    private companion object {
        const val KEY_CLIENT_ID = "soundcloud_client_id"
        const val KEY_OAUTH_TOKEN = "soundcloud_oauth_token"
        const val KEY_USER_ID = "soundcloud_user_id"
    }
}
