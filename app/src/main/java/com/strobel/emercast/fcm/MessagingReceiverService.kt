package com.strobel.emercast.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MessagingReceiverService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        Log.i(this.javaClass.name, "Message Received: " + message.messageId + " " + message.data)
    }

    override fun onNewToken(token: String) {
        Log.i(this.javaClass.name, "New FCM token: $token")
    }
}