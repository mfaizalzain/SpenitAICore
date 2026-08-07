package com.fmz.spenitaicore.util

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import java.io.File

object FileUtils {

    /**
     * Copies a shared content URI to the app's cache directory.
     * Returns a File pointing to the local copy, or null on failure.
     */
    fun copySharedFileToCache(context: Context, sourceUri: Uri, prefix: String = "shared"): File? {
        return try {
            copyUriToDirectory(context, sourceUri, context.cacheDir, prefix)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun copySharedFileToImports(context: Context, sourceUri: Uri, prefix: String = "shared"): File? {
        return try {
            val importsDir = File(context.filesDir, "shared_imports")
            if (!importsDir.exists()) importsDir.mkdirs()
            copyUriToDirectory(context, sourceUri, importsDir, prefix)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun copyUriToDirectory(
        context: Context,
        sourceUri: Uri,
        directory: File,
        prefix: String
    ): File? {
        return try {
            val mimeType = context.contentResolver.getType(sourceUri)
            val displayName = getDisplayName(context, sourceUri) ?: "${prefix}_${System.currentTimeMillis()}"
            val extension = when {
                mimeType == "application/pdf" -> ".pdf"
                mimeType?.startsWith("image/") == true -> ".jpg"
                else -> displayName.substringAfterLast('.', "").lowercase().let { ext ->
                    when (ext) {
                        "pdf" -> ".pdf"
                        "jpg", "jpeg", "png", "webp", "heic", "heif" -> ".jpg"
                        "csv" -> ".csv"
                        else -> ".bin"
                    }
                }
            }
            val safeName = displayName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
            val nameWithoutExt = safeName.substringBeforeLast('.', safeName)
            val destFile = File(directory, "${prefix}_${System.currentTimeMillis()}_$nameWithoutExt$extension")

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
     * Checks if a PDF file is password-protected by attempting to open it with PdfRenderer.
     * Returns true if a SecurityException is thrown, indicating the PDF requires a password.
     */
    fun isPdfPasswordProtected(context: Context, filePath: String): Boolean {
        return try {
            val fd: ParcelFileDescriptor = if (filePath.startsWith("content://")) {
                context.contentResolver.openFileDescriptor(Uri.parse(filePath), "r") ?: return false
            } else if (filePath.startsWith("file://")) {
                val path = Uri.parse(filePath).path ?: return false
                ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY)
            }
            try {
                val renderer = PdfRenderer(fd)
                renderer.close()
                false
            } catch (e: SecurityException) {
                Log.w("FileUtils", "PDF is password-protected: $filePath")
                true
            } finally {
                try { fd.close() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            false
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

    /**
     * Deletes a scanned/camera image owned by this app (files under
     * filesDir/images). Files under shared_imports/ are intentionally
     * left alone because shared import records still reference them.
     */
    fun deleteScannedImage(context: Context, imagePath: String?) {
        if (imagePath.isNullOrEmpty()) return
        try {
            val file = File(imagePath)
            val imagesDir = File(context.filesDir, "images").canonicalPath
            if (file.canonicalPath.startsWith(imagesDir) && file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {
        }
    }
}
