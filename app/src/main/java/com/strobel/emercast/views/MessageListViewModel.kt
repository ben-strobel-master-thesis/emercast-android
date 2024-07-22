package com.strobel.emercast.views

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import com.strobel.emercast.ble.BLEAdvertiserService
import com.strobel.emercast.db.models.BroadcastMessage
import com.strobel.emercast.db.repositories.BroadcastMessagesRepository

class MessageListViewModel(private val repo: BroadcastMessagesRepository): ViewModel() {
    val messageList = mutableStateListOf<BroadcastMessage>()
    var bleAdvertiserService: BLEAdvertiserService? = null

    fun setBLE(bleAdvertiserService: BLEAdvertiserService?) {
        this.bleAdvertiserService = bleAdvertiserService;
        updateBLEMessagesHash()
    }

    fun fetchAllMessages() {
        Log.d(this.javaClass.name, "Fetching all messages from db")
        messageList.clear()
        val messages = repo.getAllMessages()
        messageList.addAll(messages)
        updateBLEMessagesHash()
    }

    @SuppressLint("MissingPermission")
    private fun updateBLEMessagesHash() {
        if(bleAdvertiserService != null) {
            bleAdvertiserService?.setCurrentHash(repo.calculateMessagesHash())
            bleAdvertiserService?.startAdvertising()
            bleAdvertiserService?.startScan()
        }
    }

    fun deleteMessage(message: BroadcastMessage) {
        repo.deleteMessage(message)
        fetchAllMessages()
    }
}