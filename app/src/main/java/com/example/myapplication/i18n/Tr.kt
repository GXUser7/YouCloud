package com.example.myapplication.i18n

import java.util.Locale

/**
 * The app's words in the language it speaks (set in its settings, see ui.Appearance): Russian, as
 * written in the code, or English from [English]. A phrase is looked up by its Russian, as gettext
 * does, so the code reads as the screen does; one missing from the table shows in Russian.
 *
 * Read when it is shown, not kept: Android redraws the app when its language changes, and every
 * phrase is looked up afresh then. Anything worked out once and kept (an enum's caption) is
 * translated where it is shown instead.
 */
fun tr(ru: String): String = if (english()) English[ru] ?: ru else ru

/** [ru] with `%s` in it, filled with [args] — in the English phrase's order, `%1$s`, when it differs. */
fun tr(ru: String, vararg args: Any?): String = String.format(Locale.ROOT, tr(ru), *args)

/** Whether the app speaks English: chosen in its settings, or the phone's language when it follows it. */
fun english(): Boolean = Locale.getDefault().language == "en"
