package com.strobel.emercast.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation.State
import androidx.work.WorkManager
import com.strobel.emercast.ble.BLEAdvertiserService.Companion.SERVICE_HASH_DATA_UUID
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.strobel.emercast.GlobalInMemoryAppStateSingleton
import com.strobel.emercast.ble.client.GattClientWorker
import com.strobel.emercast.ble.enums.GattRoleEnum
import com.strobel.emercast.ble.server.GattServerWorker
import com.strobel.emercast.db.EmercastDbHelper
import com.strobel.emercast.db.repositories.BroadcastMessagesRepository
import java.util.UUID

class BLEScanReceiver : BroadcastReceiver() {

    // TODO (Temporarily) Blacklist mac addresses when connection couldn't be established after multiple tries

    override fun onReceive(context: Context, intent: Intent) {
        val globalAppStateSingleton = GlobalInMemoryAppStateSingleton.getInstance()
        if(globalAppStateSingleton.gattRole != GattRoleEnum.UNDETERMINED) return

        val currentHash = intent.getByteArrayExtra("currentHash")

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(this.javaClass.name, "No bluetooth connect permissions")
            return
        }

        val results = intent.getScanResults()
        val filteredResults = results.filter { x -> x.scanRecord != null && !x.scanRecord?.getServiceData(ParcelUuid(SERVICE_HASH_DATA_UUID)).contentEquals(currentHash) }
        Log.d(this.javaClass.name, "Total Devices: ${results.size} Filtered Devices: ${filteredResults.size} | CurrentHash: ${currentHash?.toString(Charsets.UTF_8)}")
        filteredResults.forEach {r -> Log.d(this.javaClass.name, "Found device ${r.device.name} at ${r.device.address} of type ${r.device.type} with bondState ${r.device.bondState}. Connectable: ${r.isConnectable}")}

        val first = filteredResults.firstOrNull() ?: return
        Log.d(this.javaClass.name, "First: ${first.device}")

        if(currentHash?.toString(Charsets.UTF_8).orEmpty() < first.scanRecord?.getServiceData(ParcelUuid(SERVICE_HASH_DATA_UUID))?.toString(Charsets.UTF_8).orEmpty()) {
            // Current device should start the GATT server and wait for a connection from the other device
            Log.d(this.javaClass.name, "Hash comparison results in server mode")
            globalAppStateSingleton.gattRole = GattRoleEnum.SERVER
            val work = OneTimeWorkRequest.Builder(GattServerWorker::class.java).build()
            WorkManager.getInstance(context).enqueueUniqueWork(GATT_SERVER_WORK_UNIQUE_NAME, ExistingWorkPolicy.KEEP, work)
        } else {
            // Current device should try to connect to the GATT server of  the other device
            Log.d(this.javaClass.name, "Hash comparison results in client mode")
            globalAppStateSingleton.gattRole = GattRoleEnum.CLIENT
            val inputData = Data.Builder()
            inputData.putString("mac", first.device.address)
            val work = OneTimeWorkRequest.Builder(GattClientWorker::class.java).setInputData(inputData.build()).build()
            WorkManager.getInstance(context).enqueueUniqueWork(GATT_CLIENT_WORK_UNIQUE_NAME, ExistingWorkPolicy.KEEP, work)
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

    companion object {
        const val GATT_SERVER_WORK_UNIQUE_NAME = "GATT_SERVER"
        const val GATT_CLIENT_WORK_UNIQUE_NAME = "GATT_CLIENT"
        private const val ACTUAL_16_BIT_GATT_SERVER_SERVICE_UUID = "b580"
        val GATT_SERVER_SERVICE_UUID: UUID = UUID.fromString("0000$ACTUAL_16_BIT_GATT_SERVER_SERVICE_UUID-0000-1000-8000-00805F9B34FB")

        val service = BluetoothGattService(GATT_SERVER_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY).also {
            it.addCharacteristic(GattServerWorker.messageToServerCharacteristic)
            it.addCharacteristic(GattServerWorker.messageToClientCharacteristic)
        }
    }
}