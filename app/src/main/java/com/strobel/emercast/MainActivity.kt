package com.strobel.emercast

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import com.strobel.emercast.ble.BLEAdvertiserService
import com.strobel.emercast.ble.BLEAdvertiserService.Companion.hasPermissions
import com.strobel.emercast.db.EmercastDbHelper
import com.strobel.emercast.db.repositories.BroadcastMessagesRepository
import com.strobel.emercast.services.BroadcastMessageService
import com.strobel.emercast.ui.theme.EmercastTheme
import com.strobel.emercast.views.MessageListView
import com.strobel.emercast.views.MessageListViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val manager: BluetoothManager? get() = applicationContext.getSystemService()!!
    private var bleAdvertiserService: BLEAdvertiserService? = null
    private var newBroadcastMessageReceiver: BroadcastReceiver? = null
    private var viewModel: MessageListViewModel? = null

    @SuppressLint("MissingPermission") // Is check with hasPermissions
    override fun onStart() {
        super.onStart()

        val requestPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { isGranted: Map<String, Boolean> ->
                val notGranted = isGranted.entries.filter { e -> !e.value }
                if (notGranted.isEmpty()) {
                    Log.i(this.javaClass.name, "Permissions granted")
                    recreate()
                } else {
                    Log.i(this.javaClass.name, "Permissions NOT granted: $notGranted")
                    Toast.makeText(this, "Cannot start without required permissions", Toast.LENGTH_LONG)
                        .show()
                    finish()
                }
            }
        if(!hasPermissions(this.applicationContext)) {
            Log.d(this.javaClass.name, "App doesn't have required permissions, requesting permissions...")
            requestPermissionLauncher.launch(BLEAdvertiserService.REQUIRED_PERMISSIONS)
            return
        }
        // Bluetooth needs to be on, otherwise this will fail, should be properly communicated in actual app
        Log.d(this.javaClass.name, "App has required permissions, binding service...")
        if(manager?.adapter != null && !manager!!.adapter.isEnabled){
            Toast.makeText(this, "Bluetooth is disabled. Enable it and restart the app", Toast.LENGTH_LONG)
                .show()
        }
        BLEAdvertiserService.startServiceOrRequestBluetoothStart(this.applicationContext)
        Intent(this, BLEAdvertiserService::class.java).also { intent ->
            run {
                val res = applicationContext.bindService(
                    intent,
                    bleServiceConnection,
                    Context.BIND_AUTO_CREATE
                )
                Log.d(this.javaClass.name, "bindService res: $res")
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
        val broadcastMessageService = BroadcastMessageService(EmercastDbHelper(applicationContext))
        lifecycleScope.launch {
            broadcastMessageService.pullBroadcastMessagesFromServer(applicationContext)
            viewModel?.fetchAllMessages()
        }
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
        try {
            unbindService(bleServiceConnection)
        } catch (_: Exception){}
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

    private val bleServiceConnection = object : ServiceConnection {
        @SuppressLint("MissingPermission")
        override fun onServiceConnected(className: ComponentName?, service: IBinder?) {
            val binder = service as BLEAdvertiserService.LocalBinder
            val boundService = binder.getService()
            bleAdvertiserService = boundService
            viewModel?.setCallback { hash ->
                run {
                    boundService.setCurrentHash(hash)
                    boundService.startAdvertising()
                    boundService.startScan()
                }
            }
        }

        override fun onServiceDisconnected(className: ComponentName?) {
            bleAdvertiserService = null
            viewModel?.setCallback(null)
        }

        override fun onBindingDied(name: ComponentName?) {
            super.onBindingDied(name)
            Log.d(this.javaClass.name, "onBindingDied $name")
        }

        override fun onNullBinding(name: ComponentName?) {
            super.onNullBinding(name)
            Log.d(this.javaClass.name, "onNullBinding $name")
        }

    }
}