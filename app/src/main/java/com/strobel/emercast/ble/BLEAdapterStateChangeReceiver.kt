package com.strobel.emercast.ble

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BLEAdapterStateChangeReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent!!.action!!.equals(BluetoothAdapter.ACTION_STATE_CHANGED, ignoreCase = true)) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> {
                    BLEAdvertiserService.startServiceOrRequestBluetoothStart(context!!)
                    Log.d(this.javaClass.name, "Starting advertising service")
                }
                BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                    Log.d(this.javaClass.name, "Stopping advertising service")
                    val stopSyncServiceIntent = Intent(context, BLEAdvertiserService::class.java)
                    context?.stopService(stopSyncServiceIntent)
                }
            }
        }
    }
}