package com.fmz.spenitaicore.util

import android.net.Uri

/**
 * Simple thread-safe holder for shared files pending import.
 * MainActivity writes incoming shared URIs here, and SharedImportsViewModel
 * drains them on init.
 */
object PendingSharedFiles {

    private val pending = mutableListOf<SharedFileEntry>()

    @Synchronized
    fun add(path: String, displayName: String) {
        pending.add(SharedFileEntry(path, displayName))
    }

    @Synchronized
    fun addAll(entries: List<SharedFileEntry>) {
        pending.addAll(entries)
    }

    @Synchronized
    fun drain(): List<SharedFileEntry> {
        val result = pending.toList()
        pending.clear()
        return result
    }

    @Synchronized
    fun hasPending(): Boolean = pending.isNotEmpty()

    data class SharedFileEntry(
        val filePath: String,
        val displayName: String
    )
}
