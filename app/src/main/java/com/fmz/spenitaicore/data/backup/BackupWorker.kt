package com.fmz.spenitaicore.data.backup

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.util.Log
import androidx.work.*
import com.fmz.spenitaicore.SpenItApp
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "BackupWorker"
        private const val UNIQUE_WORK_NAME = "spenit_nightly_backup"

        /**
         * Schedule a nightly backup at ~2 AM.
         * Call this every app startup — WorkManager handles idempotency.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<BackupWorker>(
                24, TimeUnit.HOURS,
                4, TimeUnit.HOURS // flex window
            )
                .setConstraints(constraints)
                .setInitialDelay(calculateDelayTo2AM(), TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(UNIQUE_WORK_NAME)
        }

        private fun calculateDelayTo2AM(): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 2)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.before(now)) {
                target.add(Calendar.DAY_OF_MONTH, 1)
            }
            return target.timeInMillis - now.timeInMillis
        }
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        val container = SpenItApp.instance.container
        val preferences = container.preferences
        val driveService = container.driveBackupService

        val accountName = preferences.getBackupAccountName() ?: return Result.failure()
        val accounts = AccountManager.get(context).getAccountsByType("com.google")
        val account = accounts.firstOrNull { it.name == accountName } ?: return Result.failure()

        Log.i(TAG, "Starting nightly backup for ${account.name}")

        try {
            // Create backup package (ZIP with db + media)
            val zipFile = driveService.createBackupPackage()
                ?: return Result.failure()

            val dateStr = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
            val fileName = "SpenIt_Backup_$dateStr.zip"
            val sizeKb = zipFile.length() / 1024

            val result = driveService.uploadBackup(account, zipFile, fileName)

            // Clean up temp ZIP
            zipFile.delete()

            if (result.success) {
                BackupNotificationHelper.showBackupSuccess(context, fileName, sizeKb)
                Log.i(TAG, "Nightly backup successful: ${result.driveFileId}")
                return Result.success()
            } else {
                BackupNotificationHelper.showBackupFailure(context, result.message)
                Log.e(TAG, "Nightly backup failed: ${result.message}")
                return Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Nightly backup error", e)
            BackupNotificationHelper.showBackupFailure(context, "Backup error: ${e.message}")
            return Result.retry()
        }
    }
}
