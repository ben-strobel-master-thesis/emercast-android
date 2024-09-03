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
import com.strobel.emercast.ble.server.GattServerCallback
import com.strobel.emercast.ble.server.GattServerWorker
import com.strobel.emercast.protobuf.BroadcastMessageInfoListPBO
import com.strobel.emercast.protobuf.BroadcastMessagePBO
import java.util.HashMap
import java.util.UUID
import java.util.concurrent.TimeoutException
import java.util.function.Consumer
import java.util.function.Function
import kotlin.concurrent.thread

class GattClientCallback(private val clientProtocolLogic: ClientProtocolLogic): BluetoothGattCallback() {

    private val lock = Object()
    private val lockMtu = Object()
    private val characteristicValueMap: HashMap<String, ByteArray> = HashMap()
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

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int,
    ) {
        super.onCharacteristicRead(gatt, characteristic, value, status)
        Log.d(this.javaClass.name, "onCharacteristicRead $status ${value.decodeToString()}")
        synchronized(lock) {
            characteristicValueMap[characteristic.uuid.toString()] = value
            lock.notifyAll()
        }
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
            val characteristic = service.getCharacteristic(UUID.fromString(id))
            val result = readCharacteristics(gatt, characteristic)
            BroadcastMessagePBO.parseFrom(result)
        }

        // TODO Get and respect MTU
        val writeBroadcastMessage: Consumer<BroadcastMessagePBO> = Consumer { message ->
            val characteristic = service.getCharacteristic(GattServerWorker.POST_BROADCAST_MESSAGE_CHARACTERISTIC_UUID)
            gatt.writeCharacteristic(characteristic,message.toByteArray(),BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        }

        thread {
            try {
                gatt.requestMtu(257) // so payload will be 256 bytes large
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
        var result = ByteArray(0)
        var intermediary = ByteArray(0)
        var first = true

        while(first || intermediary.size >= GattServerCallback.CHARACTERISTIC_CHUNK_SIZE) {
            first = false
            gatt.readCharacteristic(characteristic) // Increasing the offset is being handled by the server (the android api doesn't expose this to the client)
            intermediary = waitForResponse(characteristic) ?: throw TimeoutException("Server didn't respond within timeout")
            Log.d(this.javaClass.name, "Did request against ${characteristic.uuid} responseSize: ${intermediary.size} totalSize: ${result.size}")
            result = result.plus(intermediary)
        }

        return result
    }

    private fun waitForResponse(characteristic: BluetoothGattCharacteristic, timeoutMs: Long = 3000): ByteArray? {
        synchronized(lock) {
            if(characteristicValueMap.containsKey(characteristic.uuid.toString())) {
                return characteristicValueMap.remove(characteristic.uuid.toString())
            }
            try {
                lock.wait(timeoutMs)
            } catch (_: InterruptedException) {}
            if(characteristicValueMap.containsKey(characteristic.uuid.toString())) {
                return characteristicValueMap.remove(characteristic.uuid.toString())
            }
            return null
        }
    }
}