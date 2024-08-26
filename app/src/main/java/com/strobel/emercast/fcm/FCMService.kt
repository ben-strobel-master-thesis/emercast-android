package com.strobel.emercast.fcm

import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.strobel.emercast.db.repositories.BroadcastMessagesRepository
import com.strobel.emercast.db.EmercastDbHelper

// https://firebase.google.com/docs/cloud-messaging/android/receive
class FCMService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(this.javaClass.name, "Message Received: " + message.messageId + " " + message.data)

        try {
            val dbHelper = EmercastDbHelper(baseContext)
            val repo = BroadcastMessagesRepository(dbHelper)
            repo.newRow(
                message.data.getValue("id"),
                message.data.getValue("created").toLong(),
                message.data.getValue("systemMessage").toBoolean(),
                message.data.getValue("forwardUntil").toLong(),
                message.data.getValue("latitude").toFloat(),
                message.data.getValue("longitude").toFloat(),
                message.data.getValue("radius").toInt(),
                message.data.getValue("category"),
                message.data.getValue("severity").toInt(),
                message.data.getValue("title"),
                message.data.getValue("content"),
                message.data.getValue("issuedAuthorityId"),
                message.data.getValue("issuerSignature"),

                System.currentTimeMillis() / 1000,
                true,
                message.data.getValue("systemMessageRegardingAuthority")
            )

            Intent().also { intent ->
                intent.setAction("com.strobel.emercast.NEW_BROADCAST_MESSAGE")
                applicationContext.sendBroadcast(intent)
            }
        } catch (ex: Exception) {
            Log.e(this.javaClass.name, ex.toString())
        }
    }

    override fun onNewToken(token: String) {
        Log.i(this.javaClass.name, "New FCM token: $token")
    }
}