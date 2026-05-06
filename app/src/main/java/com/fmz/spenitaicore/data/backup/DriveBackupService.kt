package com.fmz.spenitaicore.data.backup

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class DriveBackupService(private val context: Context) {

    companion object {
        private const val TAG = "DriveBackup"
        private const val DRIVE_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.file"
        private const val APP_DATA_FOLDER = "SpenItBackups"
    }

    data class BackupResult(
        val success: Boolean,
        val message: String,
        val driveFileId: String? = null
    )

    data class BackupFile(
        val id: String,
        val name: String,
        val sizeBytes: Long,
        val createdTime: String
    )

    /**
     * Request Drive file-scope authorization for the given account.
     * May throw [UserRecoverableAuthException] — the caller must show
     * the consent dialog via [UserRecoverableAuthException.intent].
     */
    @Throws(UserRecoverableAuthException::class)
    fun authorizeDrive(account: Account): String {
        return GoogleAuthUtil.getToken(context, account, DRIVE_SCOPE)
    }

    /**
     * Get a fresh Drive token for the account. Cached by GoogleAuthUtil.
     * Returns null if the account doesn't have Drive access.
     */
    fun getDriveToken(account: Account): String? {
        return try {
            GoogleAuthUtil.getToken(context, account, DRIVE_SCOPE)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get Drive token", e)
            null
        }
    }

    /**
     * Upload a backup file to Google Drive in the app's dedicated folder.
     */
    fun uploadBackup(
        account: Account,
        backupFile: File,
        fileName: String
    ): BackupResult {
        return try {
            val token = GoogleAuthUtil.getTokenWithNotification(
                context, account, DRIVE_SCOPE, null
            ) ?: return BackupResult(false, "Failed to get Drive access token")

            // Find or create the SpenItBackups folder
            val folderId = findFolderId(token, APP_DATA_FOLDER)
                ?: return BackupResult(false, "Failed to create backup folder")

            // Upload the file
            val fileId = uploadFile(token, folderId, backupFile, fileName)
                ?: return BackupResult(false, "Upload failed")

            BackupResult(true, "Backup uploaded", fileId)
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed", e)
            BackupResult(false, "Upload failed: ${e.message}")
        }
    }

    /**
     * Get the list of signed-in Google accounts on the device.
     */
    fun getGoogleAccounts(): List<Account> {
        return try {
            AccountManager.get(context)
                .getAccountsByType("com.google")
                .toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * List all backup files in the app's Drive folder, newest first.
     */
    fun listBackups(account: Account): List<BackupFile> {
        return try {
            val token = GoogleAuthUtil.getTokenWithNotification(
                context, account, DRIVE_SCOPE, null
            ) ?: return emptyList()

            val folderId = findFolderId(token, APP_DATA_FOLDER)
                ?: return emptyList()

            val query = "name contains 'SpenIt_Backup_' and " +
                    "'$folderId' in parents and trashed=false"
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?" +
                    "q=$encoded&fields=files(id,name,size,createdTime)" +
                    "&orderBy=createdTime desc&pageSize=50"

            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
            }
            val response = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val json = JSONObject(response)
            val files = json.optJSONArray("files") ?: return emptyList()

            (0 until files.length()).map { i ->
                val f = files.getJSONObject(i)
                BackupFile(
                    id = f.getString("id"),
                    name = f.optString("name", "Unknown"),
                    sizeBytes = f.optLong("size", 0),
                    createdTime = f.optString("createdTime", "")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list backups", e)
            emptyList()
        }
    }

    /**
     * Download a backup file from Drive to a local temp file.
     * Returns the local file, or null on failure.
     */
    fun downloadBackup(account: Account, fileId: String): File? {
        return try {
            val token = GoogleAuthUtil.getTokenWithNotification(
                context, account, DRIVE_SCOPE, null
            ) ?: return null

            val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 30000
                readTimeout = 120000
            }

            if (conn.responseCode != 200) {
                Log.e(TAG, "Download failed: ${conn.responseCode}")
                conn.disconnect()
                return null
            }

            val outFile = File(context.cacheDir, "restore_${System.currentTimeMillis()}.db")
            conn.inputStream.use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            conn.disconnect()
            outFile
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            null
        }
    }

    /**
     * Find the folder ID without creating it (for read-only operations).
     */
    private fun findFolderId(token: String, folderName: String): String? {
        // Search for existing folder
        val searchUrl = "https://www.googleapis.com/drive/v3/files?" +
                "q=name='$folderName'+and+mimeType='application/vnd.google-apps.folder'" +
                "+and+trashed=false&fields=files(id,name)"
        try {
            val conn = (URL(searchUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
            }
            val response = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val json = JSONObject(response)
            val files = json.optJSONArray("files")
            if (files != null && files.length() > 0) {
                return files.getJSONObject(0).getString("id")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to search for folder, will create", e)
        }

        // Create folder
        val createUrl = "https://www.googleapis.com/drive/v3/files"
        val metadata = JSONObject().apply {
            put("name", folderName)
            put("mimeType", "application/vnd.google-apps.folder")
        }
        try {
            val conn = (URL(createUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            conn.outputStream.bufferedWriter().use { it.write(metadata.toString()) }
            val response = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            return JSONObject(response).getString("id")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create folder", e)
            return null
        }
    }

    private fun uploadFile(
        token: String,
        folderId: String,
        file: File,
        fileName: String
    ): String? {
        val boundary = "SpenItBackupBoundary_${System.currentTimeMillis()}"

        val metadataJson = JSONObject().apply {
            put("name", fileName)
            put("parents", org.json.JSONArray().apply { put(folderId) })
        }.toString()

        val url = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            doOutput = true
            connectTimeout = 30000
            readTimeout = 60000
        }

        conn.outputStream.use { os ->
            os.write("--$boundary\r\n".toByteArray())
            os.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
            os.write(metadataJson.toByteArray())
            os.write("\r\n--$boundary\r\n".toByteArray())
            os.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
            FileInputStream(file).use { it.copyTo(os) }
            os.write("\r\n--$boundary--\r\n".toByteArray())
        }

        val responseCode = conn.responseCode
        val responseBody = if (responseCode in 200..299) {
            conn.inputStream.bufferedReader().readText()
        } else {
            conn.errorStream?.bufferedReader()?.readText() ?: ""
        }
        conn.disconnect()

        if (responseCode == 200) {
            return JSONObject(responseBody).getString("id")
        }

        Log.e(TAG, "Upload failed: $responseCode $responseBody")
        return null
    }
}
