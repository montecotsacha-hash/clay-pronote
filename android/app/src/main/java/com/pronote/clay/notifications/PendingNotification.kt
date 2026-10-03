package com.pronote.clay.notifications

/**
 * A notification produced by a sync, handed to the Activity which owns the
 * Context required to actually post it.
 */
data class PendingNotification(val title: String, val body: String)
