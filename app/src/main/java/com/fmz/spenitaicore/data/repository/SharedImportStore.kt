package com.fmz.spenitaicore.data.repository

import android.content.Context
import com.fmz.spenitaicore.data.db.entity.SharedImportItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class SharedImportStore(context: Context) {

    private val file = File(context.filesDir, "shared_imports.json")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Synchronized
    fun load(): List<SharedImportItem> {
        if (!file.exists()) return emptyList()
        return try {
            json.decodeFromString<List<SharedImportItem>>(file.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun save(items: List<SharedImportItem>) {
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        tempFile.writeText(json.encodeToString(items))
        if (!tempFile.renameTo(file)) {
            file.writeText(json.encodeToString(items))
            tempFile.delete()
        }
    }

    @Synchronized
    fun add(item: SharedImportItem) {
        val existing = load()
        save(existing + item)
    }
}
