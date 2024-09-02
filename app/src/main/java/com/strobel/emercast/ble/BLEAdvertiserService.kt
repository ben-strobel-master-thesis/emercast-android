package com.strobel.emercast.ble

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
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
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.strobel.emercast.db.EmercastDbHelper
import com.strobel.emercast.db.repositories.BroadcastMessagesRepository
import java.util.UUID

class BLEAdvertiserService: Service() {

    private var adapter: BluetoothAdapter? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    private var currentHash: ByteArray = ByteArray(16) { _ -> 0 }
    private val binder = LocalBinder()


    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(this.javaClass.name, "Starting ${this.javaClass.name}")

        if(!hasPermissions(this.applicationContext)) {
            Log.d(this.javaClass.name, "Service doesn't have required permissions. Aborting...")
            Toast.makeText(this.applicationContext, "Service doesn't have required permissions. Aborting...", Toast.LENGTH_SHORT).show()
            return START_NOT_STICKY
        }
        Log.d(this.javaClass.name, "Service has required permissions. Starting...")
        Toast.makeText(this.applicationContext, "Service has required permissions. Starting...", Toast.LENGTH_SHORT).show()

        val bluetoothManager = this.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        adapter = bluetoothManager.adapter

        advertiser = adapter!!.bluetoothLeAdvertiser
        scanner = adapter!!.bluetoothLeScanner

        val dbHelper = EmercastDbHelper(applicationContext)
        val repo = BroadcastMessagesRepository(dbHelper)
        setCurrentHash(repo.getMessageChainHashForBLEAdvertisement())

        startScan()
        startAdvertising()

        Log.d(this.javaClass.name, "Started ${this.javaClass.name}")

        return START_STICKY
    }

    fun setCurrentHash(currentHash: ByteArray) {
        this.currentHash = currentHash
    }

    @SuppressLint("MissingPermission") // Is checked by calling function
    fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            // Hash is being truncated -> higher collision probability, but rather more false positives than false negatives -> false positives will be resolved on connection
            .addServiceData(ParcelUuid(SERVICE_HASH_DATA_UUID), currentHash)
            .build()

        advertiser?.stopAdvertising(SampleAdvertiseCallback)
        advertiser?.startAdvertising(settings, data, SampleAdvertiseCallback)
        Log.d(this.javaClass.name, "Started Advertising with hash ${currentHash.toString(Charsets.UTF_8)}")
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan(): PendingIntent? {
        val scanSettings: ScanSettings = ScanSettings.Builder()
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            // If not set the results will be batch while the screen is off till the screen is turned on again
            // .setReportDelay(3000)
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

        // Create the pending intent that will be invoked when the scan happens and the filters matches
        val resultIntent = PendingIntent.getBroadcast(
            this.applicationContext,
            1,
            Intent(this.applicationContext, BLEScanReceiver::class.java)
                .putExtra("currentHash", currentHash),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

        // We only want the devices running our GATTServerSample
        val scanFilters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID))
                .build(),
        )
        scanner?.startScan(scanFilters, scanSettings, resultIntent)
        Log.d(this.javaClass.name, "Started Scanning")
        return resultIntent
    }

    object SampleAdvertiseCallback : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i("BLE_EXPERIMENT_MAIN_ACTIVITY", "Started advertising")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e("BLE_EXPERIMENT_MAIN_ACTIVITY", "Failed to start advertising: $errorCode")
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): BLEAdvertiserService = this@BLEAdvertiserService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    companion object {
        const val ACTUAL_16_BIT_SERVICE_UUID = "b570"
        const val ACTUAL_16_BIT_HASH_DATA_UUID = "b571"
        val SERVICE_UUID: UUID = UUID.fromString("0000${ACTUAL_16_BIT_SERVICE_UUID}-0000-1000-8000-00805F9B34FB")
        val SERVICE_HASH_DATA_UUID: UUID = UUID.fromString("0000${ACTUAL_16_BIT_HASH_DATA_UUID}-0000-1000-8000-00805F9B34FB")
        val TAG = "BLEAdvertiserService"
        val REQUIRED_PERMISSIONS: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.POST_NOTIFICATIONS
        ) else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) else arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH
        )

        fun hasPermissions(context: Context): Boolean {
            for (permission in REQUIRED_PERMISSIONS) {
                if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false
                }
            }
            return true
        }

        fun startServiceOrRequestBluetoothStart(context: Context) {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = bluetoothManager.adapter
            if(adapter == null || !adapter.isEnabled) {
                Log.d("startServiceOrRequestBluetoothStart", "Bluetooth is not enabled, aborting...")
            } else {
                Log.d("startServiceOrRequestBluetoothStart", "Bluetooth is enabled are granted, starting BLEAdvertiserService...")
                context.startService(Intent(context, BLEAdvertiserService::class.java))
            }
        }
    }
}