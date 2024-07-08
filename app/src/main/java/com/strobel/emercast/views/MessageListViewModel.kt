package com.strobel.emercast.views

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import com.strobel.emercast.db.models.BroadcastMessage
import com.strobel.emercast.db.repositories.BroadcastMessagesRepository

class MessageListViewModel(private val repo: BroadcastMessagesRepository): ViewModel() {
    val messageList = mutableStateListOf<BroadcastMessage>()

    fun fetchAllMessages() {
        Log.d(this.javaClass.name, "Fetching all messages from db")
        messageList.clear()
        messageList.addAll(repo.getAllMessages())
    }

    fun deleteMessage(message: BroadcastMessage) {
        repo.deleteMessage(message)
        fetchAllMessages()
    }
}