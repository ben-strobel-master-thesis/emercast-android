package com.strobel.emercast.ble.client

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.strobel.emercast.GlobalInMemoryAppStateSingleton
import com.strobel.emercast.ble.enums.GattRoleEnum
import com.strobel.emercast.ble.protocol.ClientProtocolLogic

class GattClientWorker(private val appContext: Context, workerParams: WorkerParameters): Worker(appContext, workerParams) {
    private val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val globalAppStateSingleton = GlobalInMemoryAppStateSingleton.getInstance()

    private var gatt: BluetoothGatt? = null

    @SuppressLint("MissingPermission")
    override fun doWork(): Result {
        val clientProtocolLogic = ClientProtocolLogic(applicationContext)

        val deviceAddress = inputData.getString("mac")
        val device = manager.adapter.getRemoteDevice(deviceAddress)
        Log.d(this.javaClass.name, "Connecting to device ${device.name} at ${device.address} of type ${device.type} with bondState ${device.bondState}")
        gatt = device.connectGatt(appContext, false, GattClientCallback(clientProtocolLogic))
        Log.d(this.javaClass.name, "Connect initialized")

        Thread.sleep(1000*15)

        Log.d(this.javaClass.name, "GattClientWorker finished")
        globalAppStateSingleton.gattRole = GattRoleEnum.UNDETERMINED
        gatt?.disconnect()
        gatt?.close()
        return Result.success()
    }

    @SuppressLint("MissingPermission")
    override fun onStopped() {
        super.onStopped()
        globalAppStateSingleton.gattRole = GattRoleEnum.UNDETERMINED
        gatt?.disconnect()
        gatt?.close()
    }
}