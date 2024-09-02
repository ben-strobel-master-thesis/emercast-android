package com.strobel.emercast.ble.protocol

import android.content.Context
import com.strobel.emercast.db.EmercastDbHelper
import com.strobel.emercast.protobuf.BroadcastMessageInfoListPBO
import com.strobel.emercast.protobuf.BroadcastMessagePBO
import com.strobel.emercast.services.BroadcastMessageService
import com.strobel.emercast.services.BroadcastMessageService.Companion.toDBO
import com.strobel.emercast.services.BroadcastMessageService.Companion.toInfoPBO
import com.strobel.emercast.services.BroadcastMessageService.Companion.toPBO
import java.util.function.Consumer

class ServerProtocolLogic(private val context: Context) {

    private val broadcastMessageService = BroadcastMessageService(EmercastDbHelper(context))

    fun onServerStarted(registerAvailableMessageId: Consumer<String>) {
        broadcastMessageService.getAllMessages(true).forEach{registerAvailableMessageId.accept(it.id)}
        broadcastMessageService.getAllMessages(false).forEach{registerAvailableMessageId.accept(it.id)}
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
    }
}