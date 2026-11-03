package com.phonapps.qrscanner

import android.app.Application
import androidx.room.Room
import com.phonapps.qrscanner.data.db.AppDatabase
import com.phonapps.qrscanner.data.repo.ScanRepository
import com.phonapps.qrscanner.data.repo.SettingsRepository

/** Manual dependency graph. No DI framework: the graph is small and explicit. */
class QrScannerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "qr-scanner.db").build()
        container = AppContainer(
            scanRepository = ScanRepository(db.scanDao()),
            settingsRepository = SettingsRepository(applicationContext)
        )
    }
}

data class AppContainer(
    val scanRepository: ScanRepository,
    val settingsRepository: SettingsRepository
)
