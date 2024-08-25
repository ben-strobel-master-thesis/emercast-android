package com.strobel.emercast.ble.server

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import androidx.core.content.getSystemService
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.strobel.emercast.GlobalInMemoryAppStateSingleton
import com.strobel.emercast.ble.BLEScanReceiver.Companion.GATT_SERVER_SERVICE_UUID
import com.strobel.emercast.ble.enums.GattRoleEnum
import java.util.UUID

class GattServerWorker(private val appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
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

    companion object {
        private const val ACTUAL_16_BIT_NEW_MESSAGE_TO_SERVER_CHARACTERISTIC_UUID = "b572"
        private const val ACTUAL_16_BIT_NEW_MESSAGE_TO_CLIENT_CHARACTERISTIC_UUID = "b573"
        val NEW_MESSAGE_TO_SERVER_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000$ACTUAL_16_BIT_NEW_MESSAGE_TO_SERVER_CHARACTERISTIC_UUID-0000-1000-8000-00805F9B34FB")
        val NEW_MESSAGE_TO_CLIENT_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000$ACTUAL_16_BIT_NEW_MESSAGE_TO_CLIENT_CHARACTERISTIC_UUID-0000-1000-8000-00805F9B34FB")

        private val messageToServerCharacteristic = BluetoothGattCharacteristic(
            NEW_MESSAGE_TO_SERVER_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ
        )

        private val messageToClientCharacteristic = BluetoothGattCharacteristic(
            NEW_MESSAGE_TO_CLIENT_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ
        )

        private val service = BluetoothGattService(GATT_SERVER_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY).also {
            it.addCharacteristic(messageToServerCharacteristic)
            it.addCharacteristic(messageToClientCharacteristic)
        }
    }
}