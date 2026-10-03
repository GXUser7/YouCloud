package com.example.myapplication.ui

import android.app.LocaleManager
import android.app.UiModeManager
import android.content.Context
import android.os.LocaleList

/**
 * The app's own theme and language, set in its settings over the phone's. Both are Android's to
 * keep for the app (per-app night mode and per-app language): set once, they hold from the next
 * start on, before anything is drawn, and Android redraws the app in them at once.
 */
internal object Appearance {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"

    const val RUSSIAN = "ru"
    const val ENGLISH = "en"

    /** [mode]: [SYSTEM], [LIGHT] or [DARK]. */
    fun applyTheme(context: Context, mode: String) {
        val manager = context.getSystemService(UiModeManager::class.java) ?: return
        manager.setApplicationNightMode(
            when (mode) {
                LIGHT -> UiModeManager.MODE_NIGHT_NO
                DARK -> UiModeManager.MODE_NIGHT_YES
                else -> UiModeManager.MODE_NIGHT_AUTO
            }
        )
    }

    /** The app's language: [SYSTEM] when it follows the phone's, else [RUSSIAN] or [ENGLISH]. */
    fun language(context: Context): String {
        val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales ?: return SYSTEM
        return if (locales.isEmpty) SYSTEM else locales[0].language
    }

    fun setLanguage(context: Context, language: String) {
        val manager = context.getSystemService(LocaleManager::class.java) ?: return
        manager.applicationLocales = if (language == SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language)
    }

    /**
     * The app spoke Russian whatever the phone's language until it could speak English too. Where
     * it was in use before, it stays Russian — now as a choice in the settings — rather than turning
     * English overnight on a phone set to English; a new install follows the phone.
     */
    fun keepLanguageOfEarlierVersion(context: Context) {
        val preferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        if (preferences.getBoolean(KEY_LANGUAGE_SETTLED, false)) return
        val usedBefore = preferences.getBoolean("onboarding_done", false) ||
            preferences.contains("soundcloud_oauth_token") ||
            preferences.contains("yandex_music_token")
        if (usedBefore && language(context) == SYSTEM) setLanguage(context, RUSSIAN)
        preferences.edit().putBoolean(KEY_LANGUAGE_SETTLED, true).apply()
    }

    private const val KEY_LANGUAGE_SETTLED = "language_settled"
}
