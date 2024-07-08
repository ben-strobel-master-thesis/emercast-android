package com.strobel.emercast.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.getSystemService
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.WorkManager
import com.strobel.emercast.ble.BLE.Companion.SERVICE_HASH_DATA_UUID
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.strobel.emercast.db.EmercastDbHelper
import com.strobel.emercast.db.repositories.BroadcastMessagesRepository
import java.util.UUID

class BLEScanReceiver : BroadcastReceiver() {

    // TODO (Temporarily) Blacklist mac addresses when connection couldn't be established after multiple tries
    var currentWork: Operation? = null

    override fun onReceive(context: Context, intent: Intent) {
        val currentHash = intent.getByteArrayExtra("currentHash")

        val results = intent.getScanResults()
        val filteredResults = results.filter { x -> x.scanRecord != null && !x.scanRecord?.getServiceData(ParcelUuid(SERVICE_HASH_DATA_UUID)).contentEquals(currentHash) }
        Log.d(this.javaClass.name, "Total Devices: ${results.size} Filtered Devices: ${filteredResults.size} | CurrentHash: ${currentHash?.toString(Charsets.UTF_8)}")

        val first = filteredResults.firstOrNull() ?: return

        if(currentHash?.toString(Charsets.UTF_8).orEmpty() < first.scanRecord?.getServiceData(ParcelUuid(SERVICE_HASH_DATA_UUID))?.toString(Charsets.UTF_8).orEmpty()) {
            if(currentWork != null) {
                if(currentWork?.result?.isDone != true) {
                    return
                }
                val work = OneTimeWorkRequest.Builder(GattServerWorker::class.java).build()
                currentWork = WorkManager.getInstance(context).beginWith(work).enqueue()
            }
        } else {
            //TODO first.device.connectGatt()
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

    internal class GattServerWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
        private val manager: BluetoothManager? get() = applicationContext.getSystemService()!!
        private lateinit var server: BluetoothGattServer

        @SuppressLint("MissingPermission")
        override fun doWork(): Result {
            server = manager!!.openGattServer(this.applicationContext, GattServerCallback(this.applicationContext, server))

            // TODO(developer): add long running task here.
            return Result.success()
        }
    }

    // TODO https://github.com/android/platform-samples/blob/main/samples/connectivity/bluetooth/ble/src/main/java/com/example/platform/connectivity/bluetooth/ble/server/GATTServerSampleService.kt#L114
    internal class GattServerCallback(context: Context, val server: BluetoothGattServer): BluetoothGattServerCallback() {
        private val dbHelper = EmercastDbHelper(context)
        private val repo = BroadcastMessagesRepository(dbHelper)
        private val gson = Gson()
        private val messages = repo.getAllMessages()

        override fun onConnectionStateChange(
            device: BluetoothDevice,
            status: Int,
            newState: Int,
        ) {

        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?,
        ) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            if(requestId >= messages.size) {
                server.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, ByteArray(0))
                return
            }
            server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, gson.toJson(messages[requestId]).toByteArray())
        }
    }

    companion object {
        const val ACTUAL_16_BIT_NEW_MESSAGE_TO_SERVER_CHARACTERISTIC_UUID = "b572"
        const val ACTUAL_16_BIT_NEW_MESSAGE_TO_CLIENT_CHARACTERISTIC_UUID = "b573"
        val NEW_MESSAGE_TO_SERVER_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000$ACTUAL_16_BIT_NEW_MESSAGE_TO_SERVER_CHARACTERISTIC_UUID-0000-1000-8000-00805F9B34FB")
        val NEW_MESSAGE_TO_CLIENT_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000$ACTUAL_16_BIT_NEW_MESSAGE_TO_CLIENT_CHARACTERISTIC_UUID-0000-1000-8000-00805F9B34FB")
    }
}