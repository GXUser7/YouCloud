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

    // The playback service renews an expired SoundCloud session on its own, writing straight to
    // these preferences. Mirror such writes, or the screen would keep using the dead token until
    // the next launch. Held in a field: preferences keep only a weak reference to listeners.
    private val credentialsListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                KEY_CLIENT_ID -> preferences.getString(KEY_CLIENT_ID, null)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { _clientId.value = it }
                KEY_OAUTH_TOKEN -> _oauthToken.value = readOauthToken()
                KEY_USER_ID -> _userId.value = readUserId()
            }
        }

    init {
        preferences.registerOnSharedPreferenceChangeListener(credentialsListener)
    }

    private val _yandexToken = MutableStateFlow(preferences.getString(KEY_YANDEX_TOKEN, "") ?: "")
    val yandexToken = _yandexToken.asStateFlow()

    private val _yandexUid = MutableStateFlow(preferences.getLong(KEY_YANDEX_UID, 0L))
    val yandexUid = _yandexUid.asStateFlow()

    // Yandex playlists can't be deleted from here — they belong to the account — so hiding is
    // local: the ids listed here are filtered out of the library carousel.
    val hiddenYandexPlaylists = MutableStateFlow(
        preferences.getStringSet(KEY_HIDDEN_YANDEX_PLAYLISTS, emptySet()).orEmpty()
    )

    fun setYandexPlaylistHidden(id: String, hidden: Boolean) {
        val updated = if (hidden) {
            hiddenYandexPlaylists.value + id
        } else {
            hiddenYandexPlaylists.value - id
        }
        hiddenYandexPlaylists.value = updated
        preferences.edit().putStringSet(KEY_HIDDEN_YANDEX_PLAYLISTS, updated).apply()
    }

    // YouTube Music: the site's session after signing in through the login page.
    private val _ytMusicAccount = MutableStateFlow(
        preferences.getString(KEY_YTM_ACCOUNT, null)?.takeIf { preferences.contains(KEY_YTM_COOKIE) }
    )
    /** The signed-in account's name, or null when YouTube Music isn't connected. */
    val ytMusicAccount = _ytMusicAccount.asStateFlow()

    fun ytMusicAuth(): YtAuth? {
        val cookie = preferences.getString(KEY_YTM_COOKIE, null)?.takeIf { it.isNotBlank() } ?: return null
        return YtAuth(
            cookie = cookie,
            visitorData = preferences.getString(KEY_YTM_VISITOR, null),
            authUser = preferences.getString(KEY_YTM_AUTH_USER, null) ?: "0"
        )
    }

    fun saveYtMusicAuth(auth: YtAuth, accountName: String?) {
        preferences.edit()
            .putString(KEY_YTM_COOKIE, auth.cookie)
            .putString(KEY_YTM_VISITOR, auth.visitorData)
            .putString(KEY_YTM_AUTH_USER, auth.authUser)
            .putString(KEY_YTM_ACCOUNT, accountName ?: "YouTube Music")
            .apply()
        _ytMusicAccount.value = accountName ?: "YouTube Music"
    }

    fun resetYtMusicAuth() {
        preferences.edit()
            .remove(KEY_YTM_COOKIE)
            .remove(KEY_YTM_VISITOR)
            .remove(KEY_YTM_AUTH_USER)
            .remove(KEY_YTM_ACCOUNT)
            .apply()
        _ytMusicAccount.value = null
    }

    // SoundCloud likes that didn't go through — typically refused by its bot protection while
    // the phone is on a VPN. The track is downloaded regardless; the like is sent again later.
    fun pendingSoundCloudLikes(): Set<Long> =
        preferences.getStringSet(KEY_PENDING_SOUNDCLOUD_LIKES, emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    fun setSoundCloudLikePending(trackId: Long, pending: Boolean) {
        val current = preferences.getStringSet(KEY_PENDING_SOUNDCLOUD_LIKES, emptySet()).orEmpty()
        val updated = if (pending) current + trackId.toString() else current - trackId.toString()
        if (updated != current) {
            preferences.edit().putStringSet(KEY_PENDING_SOUNDCLOUD_LIKES, updated).apply()
        }
    }

    val showDebugPercentage = MutableStateFlow(preferences.getBoolean(KEY_SHOW_DEBUG_PERCENTAGE, false))

    fun setShowDebugPercentage(enabled: Boolean) {
        showDebugPercentage.value = enabled
        preferences.edit().putBoolean(KEY_SHOW_DEBUG_PERCENTAGE, enabled).apply()
    }

    // The backdrop's shapes lean and wobble with the phone. Off unregisters the accelerometer
    // altogether rather than ignoring it, so a disabled effect costs nothing.
    val backgroundMotion = MutableStateFlow(preferences.getBoolean(KEY_BACKGROUND_MOTION, true))

    fun setBackgroundMotion(enabled: Boolean) {
        backgroundMotion.value = enabled
        preferences.edit().putBoolean(KEY_BACKGROUND_MOTION, enabled).apply()
    }

    // The full-screen player takes its palette from the cover instead of the wallpaper. Only the
    // player: the rest of the app keeps the system colours either way.
    val playerCoverColors = MutableStateFlow(preferences.getBoolean(KEY_PLAYER_COVER_COLORS, true))

    fun setPlayerCoverColors(enabled: Boolean) {
        playerCoverColors.value = enabled
        preferences.edit().putBoolean(KEY_PLAYER_COVER_COLORS, enabled).apply()
    }

    // Music videos in the cover's place, and Yandex's looping videoshots behind the player.
    val playerVideos = MutableStateFlow(preferences.getBoolean(KEY_PLAYER_VIDEOS, true))

    fun setPlayerVideos(enabled: Boolean) {
        playerVideos.value = enabled
        preferences.edit().putBoolean(KEY_PLAYER_VIDEOS, enabled).apply()
    }

    // Which videos, when [playerVideos] is on: YouTube's music videos (a YouTube Music song's own,
    // or one found for a Yandex track), and Yandex's videoshots and clip loops.
    val videoYouTube = MutableStateFlow(preferences.getBoolean(KEY_VIDEO_YOUTUBE, true))

    fun setVideoYouTube(enabled: Boolean) {
        videoYouTube.value = enabled
        preferences.edit().putBoolean(KEY_VIDEO_YOUTUBE, enabled).apply()
    }

    val videoYandex = MutableStateFlow(preferences.getBoolean(KEY_VIDEO_YANDEX, true))

    fun setVideoYandex(enabled: Boolean) {
        videoYandex.value = enabled
        preferences.edit().putBoolean(KEY_VIDEO_YANDEX, enabled).apply()
    }

    // The glow of blurred copies around a video in the cover's place and behind the panel.
    val videoGlow = MutableStateFlow(preferences.getBoolean(KEY_VIDEO_GLOW, true))

    fun setVideoGlow(enabled: Boolean) {
        videoGlow.value = enabled
        preferences.edit().putBoolean(KEY_VIDEO_GLOW, enabled).apply()
    }

    // Downloaded tracks' videos kept on the phone, to play offline.
    val videoDownload = MutableStateFlow(preferences.getBoolean(KEY_VIDEO_DOWNLOAD, true))

    fun setVideoDownload(enabled: Boolean) {
        videoDownload.value = enabled
        preferences.edit().putBoolean(KEY_VIDEO_DOWNLOAD, enabled).apply()
    }

    // Asking GitHub for a new version twice a day; see UpdateRepository.
    val updateAutoCheck = MutableStateFlow(preferences.getBoolean(KEY_UPDATE_AUTO_CHECK, true))

    fun setUpdateAutoCheck(enabled: Boolean) {
        updateAutoCheck.value = enabled
        preferences.edit().putBoolean(KEY_UPDATE_AUTO_CHECK, enabled).apply()
    }

    val lastUpdateCheck: Long
        get() = preferences.getLong(KEY_LAST_UPDATE_CHECK, 0L)

    fun markUpdateChecked() {
        preferences.edit().putLong(KEY_LAST_UPDATE_CHECK, System.currentTimeMillis()).apply()
    }

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
        const val KEY_HIDDEN_YANDEX_PLAYLISTS = "hidden_yandex_playlists"
        const val KEY_PENDING_SOUNDCLOUD_LIKES = "pending_soundcloud_likes"
        const val KEY_YTM_COOKIE = "ytmusic_cookie"
        const val KEY_YTM_VISITOR = "ytmusic_visitor_data"
        const val KEY_YTM_AUTH_USER = "ytmusic_auth_user"
        const val KEY_YTM_ACCOUNT = "ytmusic_account"
        const val KEY_SHOW_DEBUG_PERCENTAGE = "show_debug_percentage"
        const val KEY_BACKGROUND_MOTION = "background_motion"
        const val KEY_PLAYER_VIDEOS = "player_videos"
        const val KEY_VIDEO_YOUTUBE = "video_youtube"
        const val KEY_VIDEO_YANDEX = "video_yandex"
        const val KEY_VIDEO_GLOW = "video_glow"
        const val KEY_VIDEO_DOWNLOAD = "video_download"
        const val KEY_PLAYER_COVER_COLORS = "player_cover_colors"
        const val KEY_UPDATE_AUTO_CHECK = "update_auto_check"
        const val KEY_LAST_UPDATE_CHECK = "last_update_check"
    }
}
