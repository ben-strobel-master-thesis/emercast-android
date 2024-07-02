package com.strobel.emercast

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.strobel.emercast.ble.BLEScanReceiver
import com.strobel.emercast.ui.theme.EmercastTheme
import java.util.UUID

const val ACTUAL_16_BIT_SERVICE_UUID = "b570"
val SERVICE_UUID: UUID = UUID.fromString("0000$ACTUAL_16_BIT_SERVICE_UUID-0000-1000-8000-00805F9B34FB")

class MainActivity : ComponentActivity() {

    private val TAG = "BLE_EXPERIMENT_MAIN_ACTIVITY"
    private val REQUIRED_PERMISSIONS: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
    ) else arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.BLUETOOTH
    )
    private val manager: BluetoothManager get() = applicationContext.getSystemService()!!
    private val advertiser: BluetoothLeAdvertiser
        get() = manager.adapter.bluetoothLeAdvertiser

    private fun hasPermissions(): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    @SuppressLint("MissingPermission") // Is check with hasPermissions
    override fun onStart() {
        super.onStart()
        val scanner = this.getSystemService<BluetoothManager>()?.adapter?.bluetoothLeScanner
        if(scanner == null) {
            Toast.makeText(this, "Bluetooth Scanner not found", Toast.LENGTH_LONG)
                .show()
            return;
        }
        val requestPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { isGranted: Map<String, Boolean> ->
                val notGranted = isGranted.entries.filter { e -> !e.value }
                if (notGranted.isEmpty()) {
                    Log.i(TAG, "Permissions granted")
                    recreate()
                } else {
                    Log.i(TAG, "Permissions NOT granted: $notGranted")
                    Toast.makeText(this, "Cannot start without required permissions", Toast.LENGTH_LONG)
                        .show()
                    finish()
                }
            }

        when {
            hasPermissions() -> {
                // Bluetooth needs to be on, otherwise this will fail, should be properly communicated in actual app
                startScan(this, scanner)
                startAdvertising()
            }
            else -> {
                requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EmercastTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    object SampleAdvertiseCallback : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i("BLE_EXPERIMENT_MAIN_ACTIVITY", "Started advertising")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e("BLE_EXPERIMENT_MAIN_ACTIVITY", "Failed to start advertising: $errorCode")
        }
    }

    @SuppressLint("MissingPermission") // Is checked by calling function
    private fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        advertiser.stopAdvertising(SampleAdvertiseCallback)
        advertiser.startAdvertising(settings, data, SampleAdvertiseCallback)
    }
}

@SuppressLint("InlinedApi")
@RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
private fun startScan(
    context: Context,
    scanner: BluetoothLeScanner,
): PendingIntent? {
    val scanSettings: ScanSettings = ScanSettings.Builder()
        // There are other modes that might work better depending on the use case
        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        // If not set the results will be batch while the screen is off till the screen is turned one again
        .setReportDelay(3000)
        // Use balanced, when in background it will be switched to low power
        .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
        .build()

    // Create the pending intent that will be invoked when the scan happens and the filters matches
    val resultIntent = PendingIntent.getBroadcast(
        context,
        1,
        Intent(context, BLEScanReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    // We only want the devices running our GATTServerSample
    val scanFilters = listOf(
        ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build(),
    )
    scanner.startScan(scanFilters, scanSettings, resultIntent)
    return resultIntent
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    EmercastTheme {
        Greeting("Android")
    }
}