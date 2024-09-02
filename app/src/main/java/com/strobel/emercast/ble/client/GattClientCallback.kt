package com.strobel.emercast.ble.client

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.strobel.emercast.ble.BLEScanReceiver.Companion.GATT_SERVER_SERVICE_UUID
import com.strobel.emercast.ble.protocol.ClientProtocolLogic
import com.strobel.emercast.ble.server.GattServerWorker
import com.strobel.emercast.protobuf.BroadcastMessageInfoListPBO
import com.strobel.emercast.protobuf.BroadcastMessagePBO
import java.util.UUID
import java.util.function.Consumer
import java.util.function.Function

class GattClientCallback(private val clientProtocolLogic: ClientProtocolLogic): BluetoothGattCallback() {

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
    @SuppressLint("MissingPermission")
    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        super.onServicesDiscovered(gatt, status)

        Log.d(this.javaClass.name, "onServicesDiscovered")
        if(gatt == null) return

        val getMessageChainHash: Function<Boolean, String> = Function { systemMessage: Boolean ->
            val service = gatt.getService(GATT_SERVER_SERVICE_UUID)
            val characteristic = service.getCharacteristic(
                if(systemMessage)
                    GattServerWorker.GET_BROADCAST_MESSAGE_SYSTEM_CHAIN_HASH_CHARACTERISTIC_UUID
                else
                    GattServerWorker.GET_BROADCAST_MESSAGE_NON_SYSTEM_CHAIN_HASH_CHARACTERISTIC_UUID
            )
            gatt.readCharacteristic(characteristic)
            characteristic.value.decodeToString()
        }

        val getCurrentBroadcastMessageInfoList: Function<Boolean, BroadcastMessageInfoListPBO> = Function { systemMessage: Boolean ->
            val service = gatt.getService(GATT_SERVER_SERVICE_UUID)
            val characteristic = service.getCharacteristic(
                if(systemMessage)
                    GattServerWorker.GET_BROADCAST_MESSAGE_SYSTEM_INFO_LIST_CHARACTERISTIC_UUID
                else
                    GattServerWorker.GET_BROADCAST_MESSAGE_NON_SYSTEM_INFO_LIST_CHARACTERISTIC_UUID
            )
            gatt.readCharacteristic(characteristic)
            BroadcastMessageInfoListPBO.parseFrom(characteristic.value)
        }

        val getBroadcastMessage: Function<String, BroadcastMessagePBO> = Function { id: String ->
            val service = gatt.getService(GATT_SERVER_SERVICE_UUID)
            val characteristic = service.getCharacteristic(UUID.fromString(id))
            gatt.readCharacteristic(characteristic)
            BroadcastMessagePBO.parseFrom(characteristic.value)
        }

        val writeBroadcastMessage: Consumer<BroadcastMessagePBO> = Consumer { message ->
            val service = gatt.getService(GATT_SERVER_SERVICE_UUID)
            val characteristic = service.getCharacteristic(GattServerWorker.POST_BROADCAST_MESSAGE_CHARACTERISTIC_UUID)
            gatt.writeCharacteristic(characteristic,message.toByteArray(),BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        }

        clientProtocolLogic.connectedToServer(
            getMessageChainHash,
            getCurrentBroadcastMessageInfoList,
            getBroadcastMessage,
            writeBroadcastMessage
        )
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int,
    ) {
        super.onCharacteristicRead(gatt, characteristic, value, status)
        Log.d(this.javaClass.name, "onCharacteristicRead ${value.decodeToString()}")
    }
}