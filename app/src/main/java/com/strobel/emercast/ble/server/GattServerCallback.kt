package com.strobel.emercast.ble.server

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServerCallback
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.strobel.emercast.db.EmercastDbHelper
import com.strobel.emercast.db.repositories.BroadcastMessagesRepository

// TODO https://github.com/android/platform-samples/blob/main/samples/connectivity/bluetooth/ble/src/main/java/com/example/platform/connectivity/bluetooth/ble/server/GATTServerSampleService.kt#L114
class GattServerCallback(private val context: Context, private val sendResponse: (BluetoothDevice, Int, Int, Int, ByteArray) -> Boolean): BluetoothGattServerCallback() {
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