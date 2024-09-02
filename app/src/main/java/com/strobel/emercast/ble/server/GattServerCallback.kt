package com.strobel.emercast.ble.server

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServerCallback
import android.content.Context
import android.util.Log
import com.strobel.emercast.ble.protocol.ServerProtocolLogic
import com.strobel.emercast.db.EmercastDbHelper
import com.strobel.emercast.db.repositories.BroadcastMessagesRepository
import com.strobel.emercast.protobuf.BroadcastMessagePBO

class GattServerCallback(
    private val sendResponse: (BluetoothDevice, Int, Int, Int, ByteArray) -> Boolean,
    private val serverProtocolLogic: ServerProtocolLogic
): BluetoothGattServerCallback() {
    override fun onConnectionStateChange(
        device: BluetoothDevice,
        status: Int,
        newState: Int,
    ) {
        super.onConnectionStateChange(device, status, newState)
        Log.d(this.javaClass.name, "onConnectionStateChange: $status $newState")
    }

    // TODO Be able to handle partial reads (when value didn't fit into mtu)
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

        if(characteristic.uuid == GattServerWorker.POST_BROADCAST_MESSAGE_CHARACTERISTIC_UUID) {
            try {
                val message = BroadcastMessagePBO.parseFrom(value)
                serverProtocolLogic.receiveBroadcastMessage(message)
            } catch (ex: Exception) {
                System.err.println("Failed onCharacteristicWriteRequest: " + ex.message)
            }
        }
    }

    override fun onCharacteristicReadRequest(
        device: BluetoothDevice?,
        requestId: Int,
        offset: Int,
        characteristic: BluetoothGattCharacteristic?,
    ) {
        super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
        Log.d(this.javaClass.name, "onCharacteristicReadRequest: $requestId $offset ${characteristic?.uuid}")

        if(characteristic?.uuid == GattServerWorker.GET_BROADCAST_MESSAGE_SYSTEM_CHAIN_HASH_CHARACTERISTIC_UUID) {
            val hash = serverProtocolLogic.getBroadcastMessageChainHash(true).encodeToByteArray()
            sendResponse(device!!, requestId, BluetoothGatt.GATT_SUCCESS, offset, hash.sliceArray(offset..<hash.size))
            return
        } else if(characteristic?.uuid == GattServerWorker.GET_BROADCAST_MESSAGE_NON_SYSTEM_CHAIN_HASH_CHARACTERISTIC_UUID) {
            val hash = serverProtocolLogic.getBroadcastMessageChainHash(false).encodeToByteArray()
            sendResponse(device!!, requestId, BluetoothGatt.GATT_SUCCESS, offset, hash.sliceArray(offset..<hash.size))
            return
        } else if(characteristic?.uuid == GattServerWorker.GET_BROADCAST_MESSAGE_SYSTEM_INFO_LIST_CHARACTERISTIC_UUID) {
            val bytes = serverProtocolLogic.getCurrentBroadcastMessageInfoList(true).toByteArray()
            sendResponse(device!!, requestId, BluetoothGatt.GATT_SUCCESS, offset, bytes.sliceArray(offset..<bytes.size))
            return
        } else if(characteristic?.uuid == GattServerWorker.GET_BROADCAST_MESSAGE_NON_SYSTEM_INFO_LIST_CHARACTERISTIC_UUID) {
            val bytes = serverProtocolLogic.getCurrentBroadcastMessageInfoList(false).toByteArray()
            sendResponse(device!!, requestId, BluetoothGatt.GATT_SUCCESS, offset, bytes.sliceArray(offset..<bytes.size))
            return
        } else {
            val bytes = serverProtocolLogic.getBroadcastMessage(characteristic!!.uuid.toString())?.toByteArray()
            if(bytes == null) {
                sendResponse(device!!, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, ByteArray(0))
                return
            }
            sendResponse(device!!, requestId, BluetoothGatt.GATT_SUCCESS, offset, bytes.sliceArray(offset..<bytes.size))
            return
        }
    }
}