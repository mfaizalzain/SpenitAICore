package com.fmz.spenit

import android.app.Application
import com.fmz.spenit.data.db.AppDatabase
import com.fmz.spenit.data.repository.ReceiptRepository
import com.fmz.spenit.data.repository.IncomeRepository
import com.fmz.spenit.data.preferences.AppPreferences
import com.fmz.spenit.ai.AiCoreService

class SpenItApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
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

    val aiCoreService: AiCoreService by lazy {
        AiCoreService(context, preferences, receiptRepository)
    }
}
