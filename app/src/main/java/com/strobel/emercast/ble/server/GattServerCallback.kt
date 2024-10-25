package com.strobel.emercast.ble.server

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.util.Log
import com.strobel.emercast.ble.BLEAdvertiserService
import com.strobel.emercast.ble.protocol.ServerProtocolLogic
import com.strobel.emercast.protobuf.BroadcastMessagePBO
import java.nio.ByteBuffer
import kotlin.math.min

class GattServerCallback(
    private val sendResponse: (BluetoothDevice, BluetoothGattCharacteristic, ByteArray) -> Int,
    private val serverProtocolLogic: ServerProtocolLogic
): BluetoothGattServerCallback() {

    private val characteristicWriteValueMap: HashMap<String, ByteArray> = HashMap()

    private var mtu = 24

    override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
        super.onMtuChanged(device, mtu)
        Log.d(this.javaClass.name, "onMtuChanged: $mtu")
        this.mtu = mtu
    }

    override fun onConnectionStateChange(
        device: BluetoothDevice,
        status: Int,
        newState: Int,
    ) {
        super.onConnectionStateChange(device, status, newState)
        Log.d(this.javaClass.name, "onConnectionStateChange: $status $newState")

        // Only one client at a time, so disconnect means worker can be torn down
        if(newState == BluetoothProfile.STATE_DISCONNECTED) {
            serverProtocolLogic.onDisconnected()
        }
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
            Log.d(this.javaClass.name, "Receiving broadcast message from client: ${characteristic.uuid} size: ${value.size}")
            var existingValue = characteristicWriteValueMap[characteristic.uuid.toString()] ?: ByteArray(0)
            existingValue += value
            characteristicWriteValueMap[characteristic.uuid.toString()] = existingValue
            if(existingValue.size >= 4 && existingValue.size >= ByteBuffer.allocate(Int.SIZE_BYTES).put(existingValue.copyOfRange(0, Int.SIZE_BYTES)).getInt(0)) {
                try {
                    val messageBytes = characteristicWriteValueMap.remove(characteristic.uuid.toString())
                    val message = BroadcastMessagePBO.parseFrom(messageBytes?.sliceArray(4..<messageBytes.size))
                    serverProtocolLogic.receiveBroadcastMessage(message)
                } catch (ex: Exception) {
                    Log.e(this.javaClass.name, "Failed onCharacteristicWriteRequest: " + ex.message)
                }
            }
        }else if(characteristic.uuid == GattServerWorker.GET_BROADCAST_MESSAGE_SYSTEM_CHAIN_HASH_CHARACTERISTIC_UUID) {
            sendCharacteristic({serverProtocolLogic.getBroadcastMessageChainHash(true).encodeToByteArray()}, {sendResponse(device, characteristic, it)})
        } else if(characteristic.uuid == GattServerWorker.GET_BROADCAST_MESSAGE_NON_SYSTEM_CHAIN_HASH_CHARACTERISTIC_UUID) {
            sendCharacteristic({serverProtocolLogic.getBroadcastMessageChainHash(false).encodeToByteArray()}, {sendResponse(device, characteristic, it)})
            return
        } else if(characteristic.uuid == GattServerWorker.GET_BROADCAST_MESSAGE_SYSTEM_INFO_LIST_CHARACTERISTIC_UUID) {
            val bytes = serverProtocolLogic.getCurrentBroadcastMessageInfoList(true).toByteArray()
            sendCharacteristic({bytes}, {sendResponse(device, characteristic, it)})
            return
        } else if(characteristic.uuid == GattServerWorker.GET_BROADCAST_MESSAGE_NON_SYSTEM_INFO_LIST_CHARACTERISTIC_UUID) {
            val bytes = serverProtocolLogic.getCurrentBroadcastMessageInfoList(false).toByteArray()
            sendCharacteristic({bytes}, {sendResponse(device, characteristic, it)})
            return
        } else {
            try {
                sendCharacteristic({serverProtocolLogic.getBroadcastMessage(characteristic.uuid.toString())!!.toByteArray()}, {sendResponse(device, characteristic, it)})
            } catch (ex: Exception) {
                Log.e(this.javaClass.name, ex.stackTraceToString())
                sendResponse(device, characteristic, ByteArray(0))
                return
            }
            return
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
    }

    override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
        super.onServiceAdded(status, service)
        Log.d(this.javaClass.name, "onServiceAdded: $status ${service?.uuid}")

        if(service?.uuid == BLEAdvertiserService.EXIST_SERVICE_UUID && status == BluetoothGatt.GATT_SUCCESS) {
            serverProtocolLogic.onServerStarted()
        }
    }

    private fun sendCharacteristic(valueGetter: () -> ByteArray, sendResponse: (ByteArray) -> Int) {
        var value = valueGetter()
        if(value.isEmpty()) {
            value = ByteArray(1)
        }
        Log.d(this.javaClass.name, "MTU for this read is ${this.mtu}")
        val chunkSize = this.mtu - 3
        var chunk = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value.size).array()
        var offset = 0

        Log.d(this.javaClass.name, "Starting to transmit characteristic. Total size: ${value.size} Value: ${value.decodeToString()}")
        while(offset < value.size) {
            val upperBound = min(value.size, offset - (if (offset == 0) Int.SIZE_BYTES else 0 ) + chunkSize)
            chunk += value.copyOfRange(offset, upperBound)
            offset = upperBound
            val result = sendResponse(chunk)
            Log.d(this.javaClass.name, "Sent chunk with size: ${chunk.size} new offset: $offset result: $result")
            chunk = ByteArray(0)
        }
        Log.d(this.javaClass.name, "Finished transmitting characteristic")
    }

    companion object {
        const val CHARACTERISTIC_CHUNK_SIZE = 256
    }
}