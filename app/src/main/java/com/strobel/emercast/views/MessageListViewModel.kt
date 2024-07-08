package com.strobel.emercast.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import com.strobel.emercast.ble.BLE
import com.strobel.emercast.db.models.BroadcastMessage
import com.strobel.emercast.db.repositories.BroadcastMessagesRepository
import java.security.MessageDigest

class MessageListViewModel(private val repo: BroadcastMessagesRepository): ViewModel() {
    val messageList = mutableStateListOf<BroadcastMessage>()
    var ble: BLE? = null

    fun setBLE(ble: BLE) {
        this.ble = ble;
        val messages = repo.getAllMessages()
        updateBLEMessagesHash(messages)
    }

    fun fetchAllMessages() {
        Log.d(this.javaClass.name, "Fetching all messages from db")
        messageList.clear()
        val messages = repo.getAllMessages()
        messageList.addAll(messages)
        updateBLEMessagesHash(messages)
    }

    @SuppressLint("MissingPermission")
    private fun updateBLEMessagesHash(messages: List<BroadcastMessage>) {
        if(ble != null) {
            ble?.setCurrentHash(calculateMessagesHash(messages))
            ble?.startAdvertising()
            ble?.startScan()
        }
    }

    private fun calculateMessagesHash(messages: List<BroadcastMessage>): ByteArray {
        val builder = StringBuilder()

        // Messages are already orderd by created timestamp (from repo)
        messages.forEach{ m ->
            builder.append(m.id)
            builder.append(m.created)
            builder.append(m.modified)
            builder.append(m.forwardUntil)
            builder.append(m.latitude)
            builder.append(m.longitude)
            builder.append(m.radius)
            builder.append(m.category)
            builder.append(m.severity)
            builder.append(m.title)
            builder.append(m.content)
        }

        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(builder.toString().toByteArray(Charsets.UTF_8))
        return hash.asList().subList(0, 16).toByteArray();
    }

    fun deleteMessage(message: BroadcastMessage) {
        repo.deleteMessage(message)
        fetchAllMessages()
    }
}