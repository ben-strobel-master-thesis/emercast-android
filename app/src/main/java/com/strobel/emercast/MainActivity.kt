package com.strobel.emercast

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import com.strobel.emercast.ble.BLE
import com.strobel.emercast.db.EmercastDbHelper
import com.strobel.emercast.db.repositories.BroadcastMessagesRepository
import com.strobel.emercast.ui.theme.EmercastTheme
import com.strobel.emercast.views.MessageListView
import com.strobel.emercast.views.MessageListViewModel

class MainActivity : ComponentActivity() {
    private val manager: BluetoothManager? get() = applicationContext.getSystemService()!!
    private val advertiser: BluetoothLeAdvertiser? get() = manager?.adapter?.bluetoothLeAdvertiser
    private var ble: BLE? = null
    private var newBroadcastMessageReceiver: BroadcastReceiver? = null
    private var viewModel: MessageListViewModel? = null

    private fun hasPermissions(): Boolean {
        for (permission in BLE.REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    @SuppressLint("MissingPermission") // Is check with hasPermissions
    override fun onStart() {
        super.onStart()

        if(manager?.adapter != null && !manager!!.adapter.isEnabled){
            Toast.makeText(this, "Bluetooth is disabled. Enable it and restart the app", Toast.LENGTH_LONG)
                .show()
            return
        }

        val scanner = manager?.adapter?.bluetoothLeScanner
        if(scanner == null) {
            Toast.makeText(this, "Bluetooth Scanner not found", Toast.LENGTH_LONG)
                .show()
            return
        }
        else {
            ble = BLE(advertiser!!, scanner, this)
            viewModel?.setBLE(ble!!)
        }
        val requestPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { isGranted: Map<String, Boolean> ->
                val notGranted = isGranted.entries.filter { e -> !e.value }
                if (notGranted.isEmpty()) {
                    Log.i(BLE.TAG, "Permissions granted")
                    recreate()
                } else {
                    Log.i(BLE.TAG, "Permissions NOT granted: $notGranted")
                    Toast.makeText(this, "Cannot start without required permissions", Toast.LENGTH_LONG)
                        .show()
                    finish()
                }
            }
        askNotificationPermission()
        when {
            hasPermissions() -> {
                // Bluetooth needs to be on, otherwise this will fail, should be properly communicated in actual app
                ble?.startScan()
                ble?.startAdvertising()
            }
            else -> {
                requestPermissionLauncher.launch(BLE.REQUIRED_PERMISSIONS)
            }
        }

        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(this.javaClass.name, "Fetching FCM registration token failed", task.exception)
                return@OnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result

            // Log and toast
            Log.d(this.javaClass.name, "Registered new FCM token $token")
            Toast.makeText(baseContext, "Registered new FCM token $token", Toast.LENGTH_SHORT).show()
        })

        FirebaseMessaging.getInstance().subscribeToTopic("test")
            .addOnCompleteListener { task ->
                var msg = "Subscribed"
                if (!task.isSuccessful) {
                    msg = "Subscribe failed"
                }
                Log.d(this.javaClass.name, msg)
                Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
            }

        newBroadcastMessageReceiver = object: BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                Log.d(this.javaClass.name, "newBroadcastMessageReceiver received broadcast")
                viewModel?.fetchAllMessages()
            }
        }
        applicationContext.registerReceiver(newBroadcastMessageReceiver, IntentFilter("com.strobel.emercast.NEW_BROADCAST_MESSAGE"), RECEIVER_EXPORTED)
        viewModel?.fetchAllMessages()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dbHelper = EmercastDbHelper(applicationContext)
        val repo = BroadcastMessagesRepository(dbHelper)
        viewModel = MessageListViewModel(repo)
        viewModel?.fetchAllMessages()
        enableEdgeToEdge()
        setContent {
            EmercastTheme {
                MessageListView(viewModel!!)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if(newBroadcastMessageReceiver != null) {
            applicationContext.unregisterReceiver(newBroadcastMessageReceiver)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.i(this.javaClass.name, "FCM is allowed to post notifications")
            // FCM SDK (and your app) can post notifications.
        } else {
            // TODO: Inform user that that your app will not show notifications.
        }
    }

    private fun askNotificationPermission() {
        // This is only necessary for API level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // FCM SDK (and your app) can post notifications.
            } else if (shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                // TODO: display an educational UI explaining to the user the features that will be enabled
                //       by them granting the POST_NOTIFICATION permission. This UI should provide the user
                //       "OK" and "No thanks" buttons. If the user selects "OK," directly request the permission.
                //       If the user selects "No thanks," allow the user to continue without notifications.
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}