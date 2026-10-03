package com.pronote.clay.core

import android.util.Log

/**
 * Internal logger.
 *
 * Allowed: "Sync started", "Sync completed", "Grades updated".
 * Forbidden: password, token, session, personal data, named grades.
 */
object Logger {
    private const val TAG = "Pronote"
    private const val REDACTED = "[redacted]"

    private val SECRET_KEYS = listOf(
        "password", "passwd", "token", "session", "secret", "credential", "authorization",
    )

    /** Strips anything that looks like a secret before it could be logged. */
    fun sanitize(key: String, value: String): String =
        if (SECRET_KEYS.any { key.lowercase().contains(it) }) REDACTED else value

    fun debug(message: String) {
        if (BuildConfigCompat.isDebug) Log.d(TAG, message)
    }

    fun info(message: String) = Log.i(TAG, message)

    fun warn(message: String) = Log.w(TAG, message)

    fun error(message: String, throwable: Throwable? = null) =
        Log.e(TAG, message, throwable)
}

/** Kept separate so Logger has no hard dependency on the generated BuildConfig. */
object BuildConfigCompat {
    val isDebug: Boolean = try {
        Class.forName("com.pronote.clay.BuildConfig")
            .getField("DEBUG")
            .getBoolean(null)
    } catch (_: Throwable) {
        true
    }
}
