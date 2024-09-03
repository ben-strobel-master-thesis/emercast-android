package com.strobel.emercast.ble.server

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServerCallback
import android.util.Log
import com.strobel.emercast.ble.protocol.ServerProtocolLogic
import com.strobel.emercast.protobuf.BroadcastMessagePBO
import java.util.HashMap

class GattServerCallback(
    private val sendResponse: (BluetoothDevice, Int, Int, Int, ByteArray) -> Boolean,
    private val serverProtocolLogic: ServerProtocolLogic
): BluetoothGattServerCallback() {

    // Accelerating offset read operations
    // Cached elements are currently never updated during a connection so don't need to be invalidated / updated
    private var cachedMessageChainSystemHash: ByteArray? = null
    private var cachedMessageChainNonSystemHash: ByteArray? = null
    private var cachedMessageInfoListSystem: ByteArray? = null
    private var cachedMessageInfoListNonSystem: ByteArray? = null
    private val cachedBroadcastMessages: HashMap<String, ByteArray> = HashMap()

    private var cachedWriteBroadcastMessage = ByteArray(0)

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

        if(characteristic.uuid == GattServerWorker.POST_BROADCAST_MESSAGE_CHARACTERISTIC_UUID) {
            try {
                val message = BroadcastMessagePBO.parseFrom(value)
                serverProtocolLogic.receiveBroadcastMessage(message)
            } catch (ex: Exception) {
                Log.e(this.javaClass.name, "Failed onCharacteristicWriteRequest: " + ex.message)
            }
        }
    }


    // TODO Should also work with other MTUs, currently will only work with MTU of 257 (256 payload) because otherwise bytes will be skipped when reading across the borders of multiples 512 (max characeristics size)
    override fun onCharacteristicReadRequest(
        device: BluetoothDevice?,
        requestId: Int,
        offset: Int,
        characteristic: BluetoothGattCharacteristic?,
    ) {
        super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
        Log.d(this.javaClass.name, "onCharacteristicReadRequest: $requestId $offset ${characteristic?.uuid}")

        if(characteristic?.uuid == GattServerWorker.GET_BROADCAST_MESSAGE_SYSTEM_CHAIN_HASH_CHARACTERISTIC_UUID) {
            val value = readCachedCharacteristic(
                cachedMessageChainSystemHash,
                {cachedMessageChainSystemHash = it},
                {serverProtocolLogic.getBroadcastMessageChainHash(true).encodeToByteArray()}
            )
            sendResponse(device!!, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            return
        } else if(characteristic?.uuid == GattServerWorker.GET_BROADCAST_MESSAGE_NON_SYSTEM_CHAIN_HASH_CHARACTERISTIC_UUID) {
            val value = readCachedCharacteristic(
                cachedMessageChainNonSystemHash,
                {cachedMessageChainNonSystemHash = it},
                {serverProtocolLogic.getBroadcastMessageChainHash(false).encodeToByteArray()}
            )
            sendResponse(device!!, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            return
        } else if(characteristic?.uuid == GattServerWorker.GET_BROADCAST_MESSAGE_SYSTEM_INFO_LIST_CHARACTERISTIC_UUID) {
            val value = readCachedCharacteristic(
                cachedMessageInfoListSystem,
                {cachedMessageInfoListSystem = it},
                {serverProtocolLogic.getCurrentBroadcastMessageInfoList(true).toByteArray()}
            )
            sendResponse(device!!, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            return
        } else if(characteristic?.uuid == GattServerWorker.GET_BROADCAST_MESSAGE_NON_SYSTEM_INFO_LIST_CHARACTERISTIC_UUID) {
            val value = readCachedCharacteristic(
                cachedMessageInfoListNonSystem,
                {cachedMessageInfoListNonSystem = it},
                {serverProtocolLogic.getCurrentBroadcastMessageInfoList(false).toByteArray()}
            )
            sendResponse(device!!, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            return
        } else {
            try {
                val value = readCachedCharacteristic(
                    cachedBroadcastMessages[characteristic!!.uuid.toString()],
                    {if(it == null) cachedBroadcastMessages.remove(characteristic.uuid.toString()) else cachedBroadcastMessages[characteristic.uuid.toString()] = it},
                    {serverProtocolLogic.getBroadcastMessage(characteristic.uuid.toString())!!.toByteArray()}
                )
                sendResponse(device!!, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            } catch (ex: Exception) {
                Log.e(this.javaClass.name, ex.stackTraceToString())
                sendResponse(device!!, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, ByteArray(0))
                return
            }
            return
        }
    }

    private fun readCachedCharacteristic(value: ByteArray?, setValue: (ByteArray?) -> Unit, valueGetter: () -> ByteArray): ByteArray {
        val currentValue = value ?: valueGetter()
        if(value == null) setValue(currentValue)

        Log.d(this.javaClass.name, "Before reading chunk, remaining size: ${currentValue.size} was null before: ${value == null}")

        val returnValue = currentValue.sliceArray(0..<currentValue.size.coerceAtMost(CHARACTERISTIC_CHUNK_SIZE))

        if(currentValue.size > returnValue.size) {
            val updated = currentValue.sliceArray(returnValue.size..<currentValue.size)
            Log.d(this.javaClass.name, "Returning size: ${returnValue.size} remaining size: ${updated.size}")
            setValue(updated)
        } else {
            setValue(ByteArray(0))
            Log.d(this.javaClass.name, "Returning size: ${returnValue.size} remaining size: null")
        }

        return returnValue
    }

    companion object {
        const val CHARACTERISTIC_CHUNK_SIZE = 256
    }
}