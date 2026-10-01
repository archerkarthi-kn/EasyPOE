package fyi.copiercode.easypos.printing

import android.app.Service
import android.content.Intent
import android.os.IBinder

class BluetoothPrinterService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
