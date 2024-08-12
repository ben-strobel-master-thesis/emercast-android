package com.strobel.emercast.ble.client

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.strobel.emercast.ble.BLEScanReceiver.Companion.GATT_SERVER_SERVICE_UUID
import com.strobel.emercast.ble.server.GattServerWorker
import com.strobel.emercast.db.EmercastDbHelper
import com.strobel.emercast.db.repositories.BroadcastMessagesRepository

class GattClientCallback(context: Context): BluetoothGattCallback() {
    private val dbHelper = EmercastDbHelper(context)
    private val repo = BroadcastMessagesRepository(dbHelper)
    private val gson = Gson()
    private val messages = repo.getAllMessages()

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

    @SuppressLint("MissingPermission")
    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        super.onServicesDiscovered(gatt, status)

        Log.d(this.javaClass.name, "onServicesDiscovered")
        val discoveredService = gatt?.getService(GATT_SERVER_SERVICE_UUID)
        if(discoveredService == null) return
        Log.d(this.javaClass.name, "Own service not null")
        val success = gatt.readCharacteristic(GattServerWorker.messageToClientCharacteristic)
        Log.d(this.javaClass.name, "Read Characteristic sucess: $success")
    }
}