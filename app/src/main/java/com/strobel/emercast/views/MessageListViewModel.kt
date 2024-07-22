package com.strobel.emercast.views

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.core.util.Consumer
import androidx.lifecycle.ViewModel
import com.strobel.emercast.ble.BLEAdvertiserService
import com.strobel.emercast.db.models.BroadcastMessage
import com.strobel.emercast.db.repositories.BroadcastMessagesRepository

class MessageListViewModel(private val repo: BroadcastMessagesRepository): ViewModel() {
    val messageList = mutableStateListOf<BroadcastMessage>()
    private var setCurrentHash: Consumer<ByteArray>? = null

    fun setCallback(callback: Consumer<ByteArray>?) {
        this.setCurrentHash = callback;
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
        if(setCurrentHash != null) {
            setCurrentHash?.accept(repo.calculateMessagesHash())
        }
    }

    fun deleteMessage(message: BroadcastMessage) {
        repo.deleteMessage(message)
        fetchAllMessages()
    }
}