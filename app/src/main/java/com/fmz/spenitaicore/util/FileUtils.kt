package com.fmz.spenitaicore.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

object FileUtils {

    /**
     * Copies a shared content URI to the app's cache directory.
     * Returns a File pointing to the local copy, or null on failure.
     */
    fun copySharedFileToCache(context: Context, sourceUri: Uri, prefix: String = "shared"): File? {
        return try {
            val mimeType = context.contentResolver.getType(sourceUri)
            val extension = when {
                mimeType == "application/pdf" -> ".pdf"
                mimeType?.startsWith("image/") == true -> ".jpg"
                else -> ".bin"
            }

            val displayName = getDisplayName(context, sourceUri) ?: "${prefix}_${System.currentTimeMillis()}"
            val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val nameWithoutExt = safeName.substringBeforeLast('.', safeName)
            val destFile = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}_$nameWithoutExt$extension")

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (destFile.exists() && destFile.length() > 0) destFile else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Gets the display name of a content URI.
     */
    fun getDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    cursor.getString(nameIndex)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
