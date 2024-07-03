package com.strobel.emercast.ble

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import java.util.UUID

class BLE(private val advertiser: BluetoothLeAdvertiser) {

    object SampleAdvertiseCallback : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i("BLE_EXPERIMENT_MAIN_ACTIVITY", "Started advertising")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e("BLE_EXPERIMENT_MAIN_ACTIVITY", "Failed to start advertising: $errorCode")
        }
    }

    @SuppressLint("MissingPermission") // Is checked by calling function
    fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        advertiser.stopAdvertising(SampleAdvertiseCallback)
        advertiser.startAdvertising(settings, data, SampleAdvertiseCallback)
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan(
        context: Context,
        scanner: BluetoothLeScanner,
    ): PendingIntent? {
        val scanSettings: ScanSettings = ScanSettings.Builder()
            // There are other modes that might work better depending on the use case
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            // If not set the results will be batch while the screen is off till the screen is turned one again
            .setReportDelay(3000)
            // Use balanced, when in background it will be switched to low power
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

        // Create the pending intent that will be invoked when the scan happens and the filters matches
        val resultIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, BLEScanReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

        // We only want the devices running our GATTServerSample
        val scanFilters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID))
                .build(),
        )
        scanner.startScan(scanFilters, scanSettings, resultIntent)
        return resultIntent
    }

    companion object {
        const val ACTUAL_16_BIT_SERVICE_UUID = "b570"
        val SERVICE_UUID: UUID = UUID.fromString("0000${Companion.ACTUAL_16_BIT_SERVICE_UUID}-0000-1000-8000-00805F9B34FB")
        val TAG = "BLE_EXPERIMENT_MAIN_ACTIVITY"
        val REQUIRED_PERMISSIONS: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) else arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH
        )
    }
}