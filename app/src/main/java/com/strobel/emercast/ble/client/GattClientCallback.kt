package com.strobel.emercast.ble.client

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.strobel.emercast.ble.BLEScanReceiver.Companion.GATT_SERVER_SERVICE_UUID
import com.strobel.emercast.ble.protocol.ClientProtocolLogic
import com.strobel.emercast.ble.server.GattServerCallback.Companion.CHARACTERISTIC_CHUNK_SIZE
import com.strobel.emercast.ble.server.GattServerWorker
import com.strobel.emercast.protobuf.BroadcastMessageInfoListPBO
import com.strobel.emercast.protobuf.BroadcastMessagePBO
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.TimeoutException
import java.util.function.Consumer
import java.util.function.Function
import kotlin.concurrent.thread
import kotlin.math.min

class GattClientCallback(private val clientProtocolLogic: ClientProtocolLogic): BluetoothGattCallback() {

    private val lock = Object()
    private val lockMtu = Object()
    private val characteristicReadValueMap: HashMap<String, ByteArray> = HashMap()
    private var mtu = 23

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

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        super.onServicesDiscovered(gatt, status)

        Log.d(this.javaClass.name, "onServicesDiscovered")
        if(gatt == null) return
        val service = gatt.getService(GATT_SERVER_SERVICE_UUID)
        if(service == null) {
            Log.d(this.javaClass.name, "Required service is not present")
            return
        }
        initiateProtocol(gatt, service)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onServiceChanged(gatt: BluetoothGatt) {
        super.onServiceChanged(gatt)

        Log.d(this.javaClass.name, "onServiceChanged")

        val service = gatt.getService(GATT_SERVER_SERVICE_UUID)
        if(service == null) {
            Log.d(this.javaClass.name, "Required service is not present")
            return
        }
        initiateProtocol(gatt, service)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        super.onCharacteristicChanged(gatt, characteristic, value)
        Log.d(this.javaClass.name, "onCharacteristicChanged $characteristic ${value.size}")
        var existingValue = characteristicReadValueMap[characteristic.uuid.toString()] ?: ByteArray(0)
        existingValue += value
        characteristicReadValueMap[characteristic.uuid.toString()] = existingValue
        if(existingValue.size >= 4 && existingValue.size >= ByteBuffer.allocate(Int.SIZE_BYTES).put(existingValue.copyOfRange(0, Int.SIZE_BYTES)).getInt(0)) {
            synchronized(lock) {
                lock.notifyAll()
            }
        }
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int,
    ) {
        super.onCharacteristicRead(gatt, characteristic, value, status)
        Log.d(this.javaClass.name, "onCharacteristicRead $status ${value.decodeToString()}")
    }

    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
        super.onMtuChanged(gatt, mtu, status)
        Log.d(this.javaClass.name, "onMtuChanged ${mtu}")

        synchronized(lockMtu) {
            this.mtu = mtu
            lockMtu.notifyAll()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @SuppressLint("MissingPermission")
    private fun initiateProtocol(gatt: BluetoothGatt, service: BluetoothGattService) {
        val getMessageChainHash: Function<Boolean, String> = Function { systemMessage: Boolean ->
            Log.d(this.javaClass.name, "Getting message chain hash")
            val characteristic = service.getCharacteristic(
                if(systemMessage)
                    GattServerWorker.GET_BROADCAST_MESSAGE_SYSTEM_CHAIN_HASH_CHARACTERISTIC_UUID
                else
                    GattServerWorker.GET_BROADCAST_MESSAGE_NON_SYSTEM_CHAIN_HASH_CHARACTERISTIC_UUID
            )
            val result = readCharacteristics(gatt, characteristic)
            result.decodeToString()
        }

        val getCurrentBroadcastMessageInfoList: Function<Boolean, BroadcastMessageInfoListPBO> = Function { systemMessage: Boolean ->
            Log.d(this.javaClass.name, "Getting broadcast message info list")
            val characteristic = service.getCharacteristic(
                if(systemMessage)
                    GattServerWorker.GET_BROADCAST_MESSAGE_SYSTEM_INFO_LIST_CHARACTERISTIC_UUID
                else
                    GattServerWorker.GET_BROADCAST_MESSAGE_NON_SYSTEM_INFO_LIST_CHARACTERISTIC_UUID
            )
            val result = readCharacteristics(gatt, characteristic)
            BroadcastMessageInfoListPBO.parseFrom(result)
        }

        val getBroadcastMessage: Function<String, BroadcastMessagePBO> = Function { id: String ->
            Log.d(this.javaClass.name, "Getting message: $id")
            val characteristic = service.getCharacteristic(UUID.fromString(id))
            val result = readCharacteristics(gatt, characteristic)
            BroadcastMessagePBO.parseFrom(result)
        }

        val writeBroadcastMessage: Consumer<BroadcastMessagePBO> = Consumer { message ->
            Log.d(this.javaClass.name, "Writing Broadcast Message: ${message.id}")
            val characteristic = service.getCharacteristic(GattServerWorker.POST_BROADCAST_MESSAGE_CHARACTERISTIC_UUID)
            val value = message.toByteArray()
            Log.d(this.javaClass.name, "MTU for this read is ${this.mtu}")
            val chunkSize = this.mtu - 3
            var chunk = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value.size).array()
            var offset = 0

            Log.d(this.javaClass.name, "Starting to transmit characteristic. Total size: ${value.size} Value: ${value.decodeToString()}")
            while(offset < value.size) {
                val upperBound = min(value.size, offset - (if (offset == 0) Int.SIZE_BYTES else 0 ) + chunkSize)
                chunk += value.copyOfRange(offset, upperBound)
                offset = upperBound
                val result = gatt.writeCharacteristic(characteristic,message.toByteArray(),BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) // Requiring ack -> packets arrive in order
                Log.d(this.javaClass.name, "Sent chunk with size: ${chunk.size} new offset: $offset result: $result")
                chunk = ByteArray(0)
            }
            Log.d(this.javaClass.name, "Finished transmitting characteristic")
        }

        thread {
            try {
                gatt.requestMtu(CHARACTERISTIC_CHUNK_SIZE+3) // so payload will be 256 bytes large
                synchronized(lockMtu) {
                    try {
                        lockMtu.wait(2000)
                    } catch (_: InterruptedException) {}
                }
                Log.d(this.javaClass.name, "Mtu for this protocol run is $mtu")

                clientProtocolLogic.connectedToServer(
                    getMessageChainHash,
                    getCurrentBroadcastMessageInfoList,
                    getBroadcastMessage,
                    writeBroadcastMessage
                )
            } catch (ex: Exception) {
                Log.e(this.javaClass.name, ex.stackTraceToString())
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun readCharacteristics(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic): ByteArray {
        gatt.setCharacteristicNotification(characteristic, true)
        gatt.writeCharacteristic(characteristic, ByteArray(0), BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        val response = waitForResponse(characteristic) ?: throw TimeoutException("Server didn't respond within timeout")
        gatt.setCharacteristicNotification(characteristic, false)
        return response
    }

    private fun waitForResponse(characteristic: BluetoothGattCharacteristic, timeoutMs: Long = 3000): ByteArray? {
        synchronized(lock) {
            try {
                lock.wait(timeoutMs)
            } catch (_: InterruptedException) {}
            val value = characteristicReadValueMap.remove(characteristic.uuid.toString())
            Log.d(this.javaClass.name, "Finished receiving characteristic. Received: ${value?.decodeToString()}")
            return value?.sliceArray(4..<value.size)
        }
    }
}