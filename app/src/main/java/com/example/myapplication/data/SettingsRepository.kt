package com.example.myapplication.data

import android.content.Context
import android.media.audiofx.Equalizer
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

    private val _yandexToken = MutableStateFlow(preferences.getString(KEY_YANDEX_TOKEN, "") ?: "")
    val yandexToken = _yandexToken.asStateFlow()

    private val _yandexUid = MutableStateFlow(preferences.getLong(KEY_YANDEX_UID, 0L))
    val yandexUid = _yandexUid.asStateFlow()

    val equalizerEnabled = MutableStateFlow(preferences.getBoolean(KEY_EQ_ENABLED, false))
    val equalizerPreset = MutableStateFlow(preferences.getString(KEY_EQ_PRESET, "Flat") ?: "Flat")
    val homeSelectedTab = MutableStateFlow(preferences.getInt(KEY_HOME_SELECTED_TAB, 0))

    fun setHomeSelectedTab(tab: Int) {
        homeSelectedTab.value = tab
        preferences.edit().putInt(KEY_HOME_SELECTED_TAB, tab).apply()
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        equalizerEnabled.value = enabled
        preferences.edit().putBoolean(KEY_EQ_ENABLED, enabled).apply()
    }

    fun setEqualizerPreset(preset: String) {
        equalizerPreset.value = preset
        preferences.edit().putString(KEY_EQ_PRESET, preset).apply()
    }

    fun getBandLevel(band: Int): Int {
        return preferences.getInt("eq_band_$band", 0)
    }

    fun setBandLevel(band: Int, level: Int) {
        preferences.edit().putInt("eq_band_$band", level).apply()
    }

    class EqualizerInfo(
        val numBands: Int,
        val minLevel: Int,
        val maxLevel: Int,
        val frequencies: List<Int>
    )

    fun getEqualizerInfo(): EqualizerInfo {
        return try {
            val eq = Equalizer(0, 0)
            val minLevel = eq.bandLevelRange[0].toInt()
            val maxLevel = eq.bandLevelRange[1].toInt()
            val numBands = eq.numberOfBands.toInt()
            val frequencies = (0 until numBands).map { band ->
                eq.getCenterFreq(band.toShort()) / 1000
            }
            eq.release()
            EqualizerInfo(numBands, minLevel, maxLevel, frequencies)
        } catch (e: Exception) {
            EqualizerInfo(
                numBands = 5,
                minLevel = -1500,
                maxLevel = 1500,
                frequencies = listOf(60, 230, 910, 4000, 14000)
            )
        }
    }

    fun oauthTokenValue(): String = _oauthToken.value
    fun userIdValue(): String = _userId.value
    fun yandexTokenValue(): String = _yandexToken.value

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

    fun saveYandexToken(value: String) {
        val cleaned = value.trim()
        _yandexToken.value = cleaned
        preferences.edit().putString(KEY_YANDEX_TOKEN, cleaned).apply()
    }

    fun resetYandexToken() {
        _yandexToken.value = ""
        preferences.edit().remove(KEY_YANDEX_TOKEN).apply()
        resetYandexUid()
    }

    fun saveYandexUid(value: Long) {
        _yandexUid.value = value
        preferences.edit().putLong(KEY_YANDEX_UID, value).apply()
    }

    fun resetYandexUid() {
        _yandexUid.value = 0L
        preferences.edit().remove(KEY_YANDEX_UID).apply()
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
        const val KEY_YANDEX_TOKEN = "yandex_music_token"
        const val KEY_YANDEX_UID = "yandex_music_uid"
        const val KEY_EQ_ENABLED = "equalizer_enabled"
        const val KEY_EQ_PRESET = "equalizer_preset"
        const val KEY_HOME_SELECTED_TAB = "home_selected_tab"
    }
}
