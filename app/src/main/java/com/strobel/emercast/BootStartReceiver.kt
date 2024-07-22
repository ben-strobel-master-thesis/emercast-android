package com.strobel.emercast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.strobel.emercast.ble.BLEAdvertiserService

class BootStartReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action!!.equals(Intent.ACTION_BOOT_COMPLETED, ignoreCase = true)) {
            Log.d(this.javaClass.name, "Boot completed, starting ${BLEAdvertiserService.javaClass.name}")
            BLEAdvertiserService.startServiceOrRequestBluetoothStart(context!!)
        }
    }
}