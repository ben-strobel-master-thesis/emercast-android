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
import com.strobel.emercast.ble.enums.GattRoleEnum
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

    internal class GattClientWorker(private val appContext: Context, workerParams: WorkerParameters): Worker(appContext, workerParams) {
        private val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        private val globalAppStateSingleton = GlobalInMemoryAppStateSingleton.getInstance()

        private var gatt: BluetoothGatt? = null

        @SuppressLint("MissingPermission")
        override fun doWork(): Result {
            val deviceAddress = inputData.getString("mac")
            val device = manager.adapter.getRemoteDevice(deviceAddress)
            Log.d(this.javaClass.name, "Connecting to device ${device.name} at ${device.address} of type ${device.type} with bondState ${device.bondState}")
            gatt = device.connectGatt(appContext, false, GattClientCallback(appContext), BluetoothDevice.TRANSPORT_AUTO)
            Log.d(this.javaClass.name, "Connect initialized")

            Thread.sleep(1000*15)

            Log.d(this.javaClass.name, "GattClientWorker finished")
            globalAppStateSingleton.gattRole = GattRoleEnum.UNDETERMINED
            gatt?.disconnect()
            gatt?.close()
            return Result.success()
        }

        @SuppressLint("MissingPermission")
        override fun onStopped() {
            super.onStopped()
            globalAppStateSingleton.gattRole = GattRoleEnum.UNDETERMINED
            gatt?.disconnect()
            gatt?.close()
        }
    }

    internal class GattServerWorker(private val appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
        private val manager: BluetoothManager? get() = applicationContext.getSystemService()!!
        private lateinit var server: BluetoothGattServer
        private val globalAppStateSingleton = GlobalInMemoryAppStateSingleton.getInstance()

        @SuppressLint("MissingPermission")
        private fun sendResponse(device: BluetoothDevice, requestId: Int, status: Int, offset: Int, value: ByteArray): Boolean {
            return server.sendResponse(device, requestId, status, offset, value)
        }

        @SuppressLint("MissingPermission")
        override fun doWork(): Result {
            Log.d(this.javaClass.name, "${this.applicationContext}")
            server = manager!!.openGattServer(this.applicationContext, GattServerCallback(this.applicationContext, ::sendResponse))
            server.addService(service)

            Thread.sleep(1000*20)

            Log.d(this.javaClass.name, "GattServerWorker finished")
            globalAppStateSingleton.gattRole = GattRoleEnum.UNDETERMINED
            server.close()
            return Result.success()
        }

        @SuppressLint("MissingPermission")
        override fun onStopped() {
            super.onStopped()
            globalAppStateSingleton.gattRole = GattRoleEnum.UNDETERMINED
            server.close()
        }
    }

    internal class GattClientCallback(context: Context): BluetoothGattCallback() {
        private val dbHelper = EmercastDbHelper(context)
        private val repo = BroadcastMessagesRepository(dbHelper)
        private val gson = Gson()
        private val messages = repo.getAllMessages()

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            Log.d(this.javaClass.name, "onConnectionStateChange $status $newState")

            Log.d(this.javaClass.name, "ConnectionStateChange $status $newState")

            if(newState == BluetoothProfile.STATE_DISCONNECTED) {
                gatt.disconnect()
                gatt.close()
            } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
                Log.d(this.javaClass.name, "Discovering services...")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)

            Log.d(this.javaClass.name, "onServicesDiscovered")
            val discoveredService = gatt?.getService(GATT_SERVER_SERVICE_UUID)
            if(discoveredService == null) return
            Log.d(this.javaClass.name, "Own service not null")
            val success = gatt.readCharacteristic(messageToClientCharacteristic)
            Log.d(this.javaClass.name, "Read Characteristic sucess: $success")
        }
    }

    // TODO https://github.com/android/platform-samples/blob/main/samples/connectivity/bluetooth/ble/src/main/java/com/example/platform/connectivity/bluetooth/ble/server/GATTServerSampleService.kt#L114
    internal class GattServerCallback(private val context: Context, private val sendResponse: (BluetoothDevice, Int, Int, Int, ByteArray) -> Boolean): BluetoothGattServerCallback() {
        private val dbHelper = EmercastDbHelper(context)
        private val repo = BroadcastMessagesRepository(dbHelper)
        // private val gson = Gson()
        // private val messages = repo.getAllMessages()

        override fun onConnectionStateChange(
            device: BluetoothDevice,
            status: Int,
            newState: Int,
        ) {
            super.onConnectionStateChange(device, status, newState)
            Log.d(this.javaClass.name, "onConnectionStateChange $status $newState")
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
            Log.d(this.javaClass.name, "onCharacteristicWriteRequest $requestId $offset ${characteristic.uuid}")
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?,
        ) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(this.javaClass.name, "No bluetooth connect permissions")
            }

            Log.d(this.javaClass.name, "onCharacteristicReadRequest $requestId $offset ${characteristic?.uuid}")
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            sendResponse(device!!, requestId, BluetoothGatt.GATT_SUCCESS, offset, "Hello world".toByteArray())
            // sendResponse(device!!, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, ByteArray(10))
            /*if(requestId >= messages.size) {
                sendResponse(device!!, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, ByteArray(0))
                return
            }
            sendResponse(device!!, requestId, BluetoothGatt.GATT_SUCCESS, offset, gson.toJson(messages[requestId]).toByteArray())*/
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