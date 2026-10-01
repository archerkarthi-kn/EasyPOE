package fyi.copiercode.easypos

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import fyi.copiercode.easypos.data.SettingsRepository
import fyi.copiercode.easypos.data.database.AppDatabase
import fyi.copiercode.easypos.printing.PrinterHelper
import fyi.copiercode.easypos.server.PosWebServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class EasyPOSApplication : Application() {

    private var webServer: PosWebServer? = null
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Bind iMin Printer Service on startup
        try {
            PrinterHelper(this).init()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Start Local Wi-Fi Audit Web Server (Port 8080)
        try {
            val database = AppDatabase.getDatabase(this, applicationScope)
            val settingsRepo = SettingsRepository(this)
            webServer = PosWebServer(
                context = this,
                orderDao = database.orderDao(),
                settingsRepository = settingsRepo,
                port = 8080
            )
            webServer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        try {
            webServer?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
