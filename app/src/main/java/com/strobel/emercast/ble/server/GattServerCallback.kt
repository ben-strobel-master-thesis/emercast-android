package com.strobel.emercast.ble.server

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServerCallback
import android.util.Log
import com.strobel.emercast.ble.protocol.ServerProtocolLogic
import com.strobel.emercast.protobuf.BroadcastMessagePBO
import java.util.HashMap
import kotlin.math.floor
import kotlin.math.min

class GattServerCallback(
    private val sendResponse: (BluetoothDevice, Int, Int, Int, ByteArray) -> Boolean,
    private val serverProtocolLogic: ServerProtocolLogic
): BluetoothGattServerCallback() {

    // Accelerating offset read operations
    // Cached elements are currently never updated during a connection and are only read once so don't need to be invalidated / updated
    private val readCacheCharacteristics: HashMap<String, Pair<ByteArray, Int>> = HashMap()
    private val writeCacheCharacteristics: HashMap<String, Pair<ByteArray, Int>> = HashMap()

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
                readCacheCharacteristics[characteristic.uuid.toString()],
                {if(it == null) readCacheCharacteristics.remove(characteristic.uuid.toString()) else readCacheCharacteristics[characteristic.uuid.toString()] = it},
                {serverProtocolLogic.getBroadcastMessageChainHash(true).encodeToByteArray()}
            )
            sendResponse(device!!, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            return
        } else if(characteristic?.uuid == GattServerWorker.GET_BROADCAST_MESSAGE_NON_SYSTEM_CHAIN_HASH_CHARACTERISTIC_UUID) {
            val value = readCachedCharacteristic(
                readCacheCharacteristics[characteristic.uuid.toString()],
                {if(it == null) readCacheCharacteristics.remove(characteristic.uuid.toString()) else readCacheCharacteristics[characteristic.uuid.toString()] = it},
                {serverProtocolLogic.getBroadcastMessageChainHash(false).encodeToByteArray()}
            )
            sendResponse(device!!, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            return
        } else if(characteristic?.uuid == GattServerWorker.GET_BROADCAST_MESSAGE_SYSTEM_INFO_LIST_CHARACTERISTIC_UUID) {
            val value = readCachedCharacteristic(
                readCacheCharacteristics[characteristic.uuid.toString()],
                {if(it == null) readCacheCharacteristics.remove(characteristic.uuid.toString()) else readCacheCharacteristics[characteristic.uuid.toString()] = it},
                {serverProtocolLogic.getCurrentBroadcastMessageInfoList(true).toByteArray()}
            )
            sendResponse(device!!, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            return
        } else if(characteristic?.uuid == GattServerWorker.GET_BROADCAST_MESSAGE_NON_SYSTEM_INFO_LIST_CHARACTERISTIC_UUID) {
            val value = readCachedCharacteristic(
                readCacheCharacteristics[characteristic.uuid.toString()],
                {if(it == null) readCacheCharacteristics.remove(characteristic.uuid.toString()) else readCacheCharacteristics[characteristic.uuid.toString()] = it},
                {serverProtocolLogic.getCurrentBroadcastMessageInfoList(false).toByteArray()}
            )
            sendResponse(device!!, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            return
        } else {
            try {
                val value = readCachedCharacteristic(
                    readCacheCharacteristics[characteristic!!.uuid.toString()],
                    {if(it == null) readCacheCharacteristics.remove(characteristic.uuid.toString()) else readCacheCharacteristics[characteristic.uuid.toString()] = it},
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

    private fun readCachedCharacteristic(value: Pair<ByteArray, Int>?, setValue: (Pair<ByteArray, Int>?) -> Unit, valueGetter: () -> ByteArray): ByteArray {
        val currentValue = value?.first ?: valueGetter()
        val currentPair = value ?: Pair(currentValue, currentValue.size)
        if(value == null) setValue(currentPair)

        Log.d(this.javaClass.name, "Before reading chunk, remaining size: ${currentValue.size} total size: ${currentPair.second} was null before: ${value == null}")

        val readUntilNow = currentPair.second - currentPair.first.size
        val next512ByteBorder = (floor(readUntilNow.toDouble()/512)+1).toInt()// Dont read across 512 byte borders, because client must read in 512 byte chunks, independent of MTU size
        val bytesUntilNextBorder = next512ByteBorder - readUntilNow

        val returnValue = currentValue.sliceArray(0..<currentValue.size.coerceAtMost(min(CHARACTERISTIC_CHUNK_SIZE, bytesUntilNextBorder)))

        if(currentValue.size > returnValue.size) {
            val updated = currentValue.sliceArray(returnValue.size..<currentValue.size)
            Log.d(this.javaClass.name, "Returning size: ${returnValue.size} remaining size: ${updated.size}")
            setValue(Pair(updated, currentPair.second))
        } else {
            setValue(Pair(ByteArray(0), currentPair.second))
            Log.d(this.javaClass.name, "Returning size: ${returnValue.size} remaining size: null")
        }

        return returnValue
    }

    companion object {
        const val CHARACTERISTIC_CHUNK_SIZE = 256
    }
}