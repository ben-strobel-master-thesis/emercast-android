package com.strobel.emercast.ble.protocol

import android.content.Context
import android.content.Intent
import com.strobel.emercast.db.EmercastDbHelper
import com.strobel.emercast.protobuf.BroadcastMessageInfoListPBO
import com.strobel.emercast.protobuf.BroadcastMessagePBO
import com.strobel.emercast.services.BroadcastMessageService
import com.strobel.emercast.services.BroadcastMessageService.Companion.toDBO
import com.strobel.emercast.services.BroadcastMessageService.Companion.toInfoPBO
import com.strobel.emercast.services.BroadcastMessageService.Companion.toPBO
import java.util.function.Consumer

class ServerProtocolLogic(private val context: Context) {

    val teardownLock = Object()
    private val broadcastMessageService = BroadcastMessageService(EmercastDbHelper(context))

    fun onServerStarted(registerAvailableMessageId: Consumer<String>) {
        broadcastMessageService.getAllMessages(true).forEach{registerAvailableMessageId.accept(it.id)}
        broadcastMessageService.getAllMessages(false).forEach{registerAvailableMessageId.accept(it.id)}
    }

    fun onDisconnected() {
        synchronized(teardownLock) { teardownLock.notifyAll() }
    }

    fun getBroadcastMessageChainHash(systemMessage: Boolean): String {
        return broadcastMessageService.getMessageChainHash(systemMessage)
    }

    // TODO This request should be paginated if this were to be deployed in production
    fun getCurrentBroadcastMessageInfoList(systemMessage: Boolean): BroadcastMessageInfoListPBO {
        return BroadcastMessageInfoListPBO
            .newBuilder()
            .addAllMessages(broadcastMessageService.getAllMessages(systemMessage).map { it.toInfoPBO() })
            .build()
    }

    fun getBroadcastMessage(id: String): BroadcastMessagePBO? {
        return broadcastMessageService.findMessageById(id)?.toPBO()
    }

    fun receiveBroadcastMessage(message: BroadcastMessagePBO) {
        broadcastMessageService.handleBroadcastMessageReceived(message.toDBO(), context)
        Intent().also { intent ->
            intent.setAction("com.strobel.emercast.NEW_BROADCAST_MESSAGE")
            context.sendBroadcast(intent)
        }
    }
}