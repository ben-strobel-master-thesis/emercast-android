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
import com.strobel.emercast.ble.protocol.ServerProtocolLogic
import java.util.UUID

class GattServerWorker(private val appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    private val manager: BluetoothManager? get() = applicationContext.getSystemService()!!
    private lateinit var server: BluetoothGattServer
    private val globalAppStateSingleton = GlobalInMemoryAppStateSingleton.getInstance()

    @SuppressLint("MissingPermission")
    private fun sendResponse(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic, value: ByteArray): Int {
        return server.notifyCharacteristicChanged(device, characteristic, true, value)
    }

    @SuppressLint("MissingPermission")
    override fun doWork(): Result {
        Log.d(this.javaClass.name, "${this.applicationContext}")
        val serverProtocolLogic = ServerProtocolLogic(this.applicationContext)

        server = manager!!.openGattServer(this.applicationContext, GattServerCallback(::sendResponse, serverProtocolLogic))

        serverProtocolLogic.onServerStarted { id ->
            addCharacteristicToService(
                service,
                UUID.fromString(id)
            )
        }
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
        val GET_BROADCAST_MESSAGE_SYSTEM_CHAIN_HASH_CHARACTERISTIC_UUID: UUID = UUID.fromString("7323fe0e-5691-4090-0000-48b1782de633")
        val GET_BROADCAST_MESSAGE_NON_SYSTEM_CHAIN_HASH_CHARACTERISTIC_UUID: UUID = UUID.fromString("7323fe0e-5691-4090-0001-48b1782de633")

        val GET_BROADCAST_MESSAGE_SYSTEM_INFO_LIST_CHARACTERISTIC_UUID: UUID = UUID.fromString("7323fe0e-5691-4090-0002-48b1782de633")
        val GET_BROADCAST_MESSAGE_NON_SYSTEM_INFO_LIST_CHARACTERISTIC_UUID: UUID = UUID.fromString("7323fe0e-5691-4090-0003-48b1782de633")

        val POST_BROADCAST_MESSAGE_CHARACTERISTIC_UUID: UUID = UUID.fromString("7323fe0e-5691-4090-0004-48b1782de633")

        private fun addCharacteristicToService(service: BluetoothGattService, uuid: UUID) {
            service.addCharacteristic(
                BluetoothGattCharacteristic(
                    uuid,
                    BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_INDICATE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ
                )
            )
        }

        private val service = BluetoothGattService(GATT_SERVER_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY).also {
            addCharacteristicToService(it, GET_BROADCAST_MESSAGE_SYSTEM_CHAIN_HASH_CHARACTERISTIC_UUID)
            addCharacteristicToService(it, GET_BROADCAST_MESSAGE_NON_SYSTEM_CHAIN_HASH_CHARACTERISTIC_UUID)
            addCharacteristicToService(it, GET_BROADCAST_MESSAGE_SYSTEM_INFO_LIST_CHARACTERISTIC_UUID)
            addCharacteristicToService(it, GET_BROADCAST_MESSAGE_NON_SYSTEM_INFO_LIST_CHARACTERISTIC_UUID)
            addCharacteristicToService(it, POST_BROADCAST_MESSAGE_CHARACTERISTIC_UUID)
        }
    }
}