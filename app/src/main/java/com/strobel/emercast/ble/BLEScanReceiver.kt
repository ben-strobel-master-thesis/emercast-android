package com.strobel.emercast.ble

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class BLEScanReceiver : BroadcastReceiver() {

    companion object {
        // Static StateFlow that caches the list of scanned devices used by our sample
        // This is an **anti-pattern** used for demo purpose and simplicity
        val devices = MutableStateFlow(emptyList<ScanResult>())
    }

    override fun onReceive(context: Context, intent: Intent) {
        val results = intent.getScanResults()
        Log.d("MPB", "Devices found: ${results.size}")

        // Update our results cached list
        if (results.isNotEmpty()) {
            devices.update { scanResults ->
                (scanResults + results).distinctBy { it.device.address }
            }
        }
    }

    /**
     * Extract the list of scan result from the intent if available
     */
    private fun Intent.getScanResults(): List<ScanResult> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(
                BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT,
                ScanResult::class.java,
            )
        } else {
            @Suppress("DEPRECATION")
            getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT)
        } ?: emptyList()
}