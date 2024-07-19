package com.strobel.emercast.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
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

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val currentHash = intent.getByteArrayExtra("currentHash")

        val results = intent.getScanResults()
        val filteredResults = results.filter { x -> x.scanRecord != null && !x.scanRecord?.getServiceData(ParcelUuid(SERVICE_HASH_DATA_UUID)).contentEquals(currentHash) }
        Log.d(this.javaClass.name, "Total Devices: ${results.size} Filtered Devices: ${filteredResults.size} | CurrentHash: ${currentHash?.toString(Charsets.UTF_8)}")

        val first = filteredResults.firstOrNull() ?: return

        if(currentHash?.toString(Charsets.UTF_8).orEmpty() < first.scanRecord?.getServiceData(ParcelUuid(SERVICE_HASH_DATA_UUID))?.toString(Charsets.UTF_8).orEmpty()) {
            // Current device should start the GATT server and wait for a connection from the other device
            Log.d(this.javaClass.name, "Hash comparison results in server mode")
            val work = OneTimeWorkRequest.Builder(GattServerWorker::class.java).build()
            WorkManager.getInstance(context).enqueueUniqueWork(GATT_SERVER_WORK_UNIQUE_NAME, ExistingWorkPolicy.KEEP, work)
        } else {
            // Current device should try to connect to the GATT server of  the other device
            Log.d(this.javaClass.name, "Hash comparison results in client mode")
            first.device.connectGatt(context, false, GattClientCallback(context))
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
        fun sendResponse(device: BluetoothDevice, requestId: Int, status: Int, offset: Int, value: ByteArray): Boolean {
            return server.sendResponse(device, requestId, status, offset, value)
        }

        @SuppressLint("MissingPermission")
        override fun doWork(): Result {
            server = manager!!.openGattServer(this.applicationContext, GattServerCallback(this.applicationContext, ::sendResponse))
            server.addService(service)

            Thread.sleep(1000*20)

            Log.d(this.javaClass.name, "GattServerWorker finished")
            return Result.success()
        }
    }

    internal class GattClientCallback(context: Context): BluetoothGattCallback() {
        private val dbHelper = EmercastDbHelper(context)
        private val repo = BroadcastMessagesRepository(dbHelper)
        private val gson = Gson()
        private val messages = repo.getAllMessages()

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(this.javaClass.name, "ConnectionStateChange $status $newState")
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            val discoveredService = gatt?.getService(GATT_SERVER_SERVICE_UUID)
            if(discoveredService == null) return
            val success = gatt.readCharacteristic(messageToClientCharacteristic)
            Log.d(this.javaClass.name, "Read Characteristic sucess: $success")
        }
    }

    // TODO https://github.com/android/platform-samples/blob/main/samples/connectivity/bluetooth/ble/src/main/java/com/example/platform/connectivity/bluetooth/ble/server/GATTServerSampleService.kt#L114
    internal class GattServerCallback(context: Context, private val sendResponse: (BluetoothDevice, Int, Int, Int, ByteArray) -> Boolean): BluetoothGattServerCallback() {
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
                sendResponse(device!!, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, ByteArray(0))
                return
            }
            sendResponse(device!!, requestId, BluetoothGatt.GATT_SUCCESS, offset, gson.toJson(messages[requestId]).toByteArray())
        }
    }

    companion object {
        const val GATT_SERVER_WORK_UNIQUE_NAME = "GATT_SERVER"
        const val GATT_CLIENT_WORK_UNIQUE_NAME = "GATT_CLIENT"
        private const val ACTUAL_16_BIT_NEW_MESSAGE_TO_SERVER_CHARACTERISTIC_UUID = "b572"
        private const val ACTUAL_16_BIT_NEW_MESSAGE_TO_CLIENT_CHARACTERISTIC_UUID = "b573"
        private const val ACTUAL_16_BIT_GATT_SERVER_SERVICE_UUID = "b580"
        val NEW_MESSAGE_TO_SERVER_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000$ACTUAL_16_BIT_NEW_MESSAGE_TO_SERVER_CHARACTERISTIC_UUID-0000-1000-8000-00805F9B34FB")
        val NEW_MESSAGE_TO_CLIENT_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000$ACTUAL_16_BIT_NEW_MESSAGE_TO_CLIENT_CHARACTERISTIC_UUID-0000-1000-8000-00805F9B34FB")
        val GATT_SERVER_SERVICE_UUID: UUID = UUID.fromString("0000$ACTUAL_16_BIT_GATT_SERVER_SERVICE_UUID-0000-1000-8000-00805F9B34FB")

        private val messageToServerCharacteristic = BluetoothGattCharacteristic(
            NEW_MESSAGE_TO_SERVER_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PROPERTY_READ
        )

        private val messageToClientCharacteristic = BluetoothGattCharacteristic(
            NEW_MESSAGE_TO_CLIENT_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PROPERTY_READ
        )

        val service = BluetoothGattService(GATT_SERVER_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY).also {
            it.addCharacteristic(messageToServerCharacteristic)
            it.addCharacteristic(messageToClientCharacteristic)
        }
    }
}