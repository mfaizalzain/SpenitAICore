package com.fmz.spenitaicore

import android.app.Application
import com.fmz.spenitaicore.data.db.AppDatabase
import com.fmz.spenitaicore.data.repository.ReceiptRepository
import com.fmz.spenitaicore.data.repository.IncomeRepository
import com.fmz.spenitaicore.data.repository.SharedImportStore
import com.fmz.spenitaicore.data.preferences.AppPreferences
import com.fmz.spenitaicore.ai.AiCoreService
import com.fmz.spenitaicore.data.auth.GoogleAuthService
import com.fmz.spenitaicore.data.export.ExportService
import com.fmz.spenitaicore.data.backup.DriveBackupService
import com.fmz.spenitaicore.data.backup.BackupNotificationHelper
import com.fmz.spenitaicore.data.backup.BackupWorker
import com.fmz.spenitaicore.data.notification.ImportNotificationHelper
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SpenItApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)

        BackupNotificationHelper.createChannel(this)
        ImportNotificationHelper.createChannel(this)

        // Initialize AdMob (async, won't block startup)
        MobileAds.initialize(this)

        // Reschedule nightly backup if enabled (survives reboots)
        CoroutineScope(Dispatchers.IO).launch {
            if (container.preferences.isBackupEnabled()) {
                BackupWorker.schedule(this@SpenItApp)
            }
        }
    }

    companion object {
        lateinit var instance: SpenItApp
            private set
    }
}

class AppContainer(context: Application) {
    val database: AppDatabase by lazy {
        AppDatabase.getInstance(context)
    }

    val preferences: AppPreferences by lazy {
        AppPreferences(context)
    }

    val receiptRepository: ReceiptRepository by lazy {
        ReceiptRepository(database.receiptDao(), database.receiptItemDao())
    }

    val incomeRepository: IncomeRepository by lazy {
        IncomeRepository(database.incomeEntryDao())
    }

    val sharedImportStore: SharedImportStore by lazy {
        SharedImportStore(context)
    }

    val aiCoreService: AiCoreService by lazy {
        AiCoreService(context, preferences, receiptRepository)
    }

    val googleAuthService: GoogleAuthService by lazy {
        GoogleAuthService(context)
    }

    val exportService: ExportService by lazy {
        ExportService(context, receiptRepository)
    }

    val driveBackupService: DriveBackupService by lazy {
        DriveBackupService(context)
    }
}
