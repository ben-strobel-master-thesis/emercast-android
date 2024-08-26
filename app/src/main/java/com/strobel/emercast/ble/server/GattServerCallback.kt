package com.strobel.emercast.ble.server

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServerCallback
import android.content.Context
import android.util.Log
import com.strobel.emercast.db.EmercastDbHelper
import com.strobel.emercast.db.repositories.BroadcastMessagesRepository

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
        Log.d(this.javaClass.name, "onConnectionStateChange: $status $newState")
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
        Log.d(this.javaClass.name, "onCharacteristicWriteRequest: $requestId $offset ${characteristic.uuid}")
    }

    override fun onCharacteristicReadRequest(
        device: BluetoothDevice?,
        requestId: Int,
        offset: Int,
        characteristic: BluetoothGattCharacteristic?,
    ) {
        super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
        Log.d(this.javaClass.name, "onCharacteristicReadRequest: $requestId $offset ${characteristic?.uuid}")
        sendResponse(device!!, requestId, BluetoothGatt.GATT_SUCCESS, offset, "Hello world".encodeToByteArray())
        // sendResponse(device!!, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, ByteArray(10))
        /*if(requestId >= messages.size) {
            sendResponse(device!!, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, ByteArray(0))
            return
        }
        sendResponse(device!!, requestId, BluetoothGatt.GATT_SUCCESS, offset, gson.toJson(messages[requestId]).toByteArray())*/
    }
}