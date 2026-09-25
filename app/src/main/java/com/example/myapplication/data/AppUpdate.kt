package com.example.myapplication.data

import java.io.File

/** One published release, reduced to what an update needs to know about it. */
data class Release(
    val version: String,
    val notes: String,
    val apkUrl: String,
    /** Zero when the source did not say — the fallback lookup has no size to give. */
    val sizeBytes: Long
)

/**
 * Where the app is in the business of updating itself.
 *
 * A sealed set rather than a bag of nullable fields: the screen draws exactly one of these at a
 * time, and "downloading, and also an error, and also up to date" is not a state the app can be in.
 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState

    /** Checked, and this is already the newest release. */
    data class UpToDate(val version: String) : UpdateState

    data class Available(val release: Release) : UpdateState

    /** [total] is zero when the server did not say how big the file is. */
    data class Downloading(val release: Release, val downloaded: Long, val total: Long) : UpdateState

    /** Downloaded whole; the installer is the next tap, and it is the user who makes it. */
    data class Ready(val release: Release, val file: File) : UpdateState

    data class Failed(val message: String) : UpdateState
}

/**
 * Comparing two version strings, which is less obvious than it looks.
 *
 * The app's `versionName` and a GitHub tag are written differently — `3.2` against `v3.2` — and a
 * debug build may carry a suffix on top (`3.2-debug`). Compared as text, a debug build of 3.2
 * would look older than the 3.2 release and be offered the same code again, and `3.10` would sort
 * before `3.9`. So: strip the decoration, read the numbers, compare them as numbers.
 */
object Version {

    /** `v3.2-debug` → `[3, 2]`. Anything unparseable in a position reads as zero. */
    fun parse(raw: String): List<Int> = raw
        .trim()
        .removePrefix("v")
        .removePrefix("V")
        .takeWhile { it != '-' && it != '+' && it != ' ' }
        .split('.')
        .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    /** Equal versions are not newer, which keeps a debug build from being told to update to itself. */
    fun isNewer(candidate: String, current: String): Boolean {
        val left = parse(candidate)
        val right = parse(current)
        for (index in 0 until maxOf(left.size, right.size)) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
