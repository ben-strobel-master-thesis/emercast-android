package com.strobel.emercast.ble.client

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.ParcelUuid
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.strobel.emercast.GlobalInMemoryAppStateSingleton
import com.strobel.emercast.ble.BLEAdvertiserService.Companion.EXIST_SERVICE_UUID
import com.strobel.emercast.ble.BLEScanReceiver
import com.strobel.emercast.ble.enums.GattRoleEnum
import com.strobel.emercast.ble.protocol.ClientProtocolLogic

class GattClientWorker(private val appContext: Context, workerParams: WorkerParameters): Worker(appContext, workerParams) {
    private val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val globalAppStateSingleton = GlobalInMemoryAppStateSingleton.getInstance()
    private val scanWaitLock = Object()
    private var foundStartedServerDevice: BluetoothDevice? = null
    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private val bluetoothScanCallback = BLEScanCallback(scanWaitLock) {
        foundStartedServerDevice = it
    }

    override fun doWork(): Result {
        val deviceAddress = inputData.getString("mac")
        val device = manager.adapter.getRemoteDevice(deviceAddress)

        startScanForStartedServer()
        try {
            synchronized(scanWaitLock) {
                scanWaitLock.wait(1000*5)
            }
        } catch (ex: Exception) {
            Log.d(this.javaClass.name, ex.stackTraceToString())
        }
        stopScanForStartedServer()
        foundStartedServerDevice?.let { onStartedServerFound(it) }

        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private fun startScanForStartedServer() {
        val bluetoothManager = this.appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        scanner = bluetoothManager.adapter!!.bluetoothLeScanner

        val scanSettings: ScanSettings = ScanSettings.Builder()
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            // If not set the results will be batch while the screen is off till the screen is turned on again
            // .setReportDelay(3000)
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

        // We only want the devices running our GATTServerSample
        val scanFilters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(EXIST_SERVICE_UUID))
                .build(),
        )
        scanner?.startScan(scanFilters, scanSettings, bluetoothScanCallback)
        Log.d(this.javaClass.name, "Started Scanning")
    }

    @SuppressLint("MissingPermission")
    private fun stopScanForStartedServer() {
        scanner?.stopScan(bluetoothScanCallback)
    }

    class BLEScanCallback(private val scanWaitLock: Object, private val setFoundStartedServerDevice: (BluetoothDevice) -> Unit) : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            setFoundStartedServerDevice(result.device)
            synchronized(scanWaitLock) {
                scanWaitLock.notifyAll()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun onStartedServerFound(device: BluetoothDevice) {
        val clientProtocolLogic = ClientProtocolLogic(applicationContext)

        Log.d(this.javaClass.name, "Connecting to device ${device.name} at ${device.address} of type ${device.type} with bondState ${device.bondState}")
        gatt = device.connectGatt(appContext, false, GattClientCallback(clientProtocolLogic))
        Log.d(this.javaClass.name, "Connect initialized")

        try {
            synchronized(clientProtocolLogic.teardownLock) {
                clientProtocolLogic.teardownLock.wait(1000*15)
            }
        } catch (ex: Exception) {
            Log.d(this.javaClass.name, ex.stackTraceToString())
        }

        Log.d(this.javaClass.name, "GattClientWorker finished")
        globalAppStateSingleton.gattRole = GattRoleEnum.UNDETERMINED
        gatt?.disconnect()
        gatt?.close()
    }

    @SuppressLint("MissingPermission")
    override fun onStopped() {
        super.onStopped()
        globalAppStateSingleton.gattRole = GattRoleEnum.UNDETERMINED
        gatt?.disconnect()
        gatt?.close()
    }
}