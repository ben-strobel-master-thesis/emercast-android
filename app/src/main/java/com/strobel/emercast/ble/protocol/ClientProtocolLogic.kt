package com.strobel.emercast.ble.protocol

import android.content.Context
import android.content.Intent
import android.util.Log
import com.strobel.emercast.db.EmercastDbHelper
import com.strobel.emercast.db.models.BroadcastMessage
import com.strobel.emercast.protobuf.BroadcastMessageInfoListPBO
import com.strobel.emercast.protobuf.BroadcastMessageInfoPBO
import com.strobel.emercast.protobuf.BroadcastMessagePBO
import com.strobel.emercast.services.BroadcastMessageService
import com.strobel.emercast.services.BroadcastMessageService.Companion.toDBO
import com.strobel.emercast.services.BroadcastMessageService.Companion.toPBO
import java.util.function.Consumer
import java.util.function.Function

class ClientProtocolLogic(private val context: Context) {

    private val broadcastMessageService = BroadcastMessageService(EmercastDbHelper(context))

    fun connectedToServer(
        getMessageChainHash: Function<Boolean, String>,
        getCurrentBroadcastMessageInfoList: Function<Boolean, BroadcastMessageInfoListPBO>,
        getBroadcastMessage: Function<String, BroadcastMessagePBO>,
        writeBroadcastMessage: Consumer<BroadcastMessagePBO>
    ) {
        val localSystemMessageChainHash = broadcastMessageService.getMessageChainHash(true)
        val localNonSystemMessageChainHash = broadcastMessageService.getMessageChainHash(false)

        val remoteSystemMessageChainHash = getMessageChainHash.apply(true)
        if(localSystemMessageChainHash != remoteSystemMessageChainHash) {
            syncBroadcastMessages(true, getCurrentBroadcastMessageInfoList, getBroadcastMessage, writeBroadcastMessage)
        }

        val remoteNonSystemMessageChainHash = getMessageChainHash.apply(false)
        if(localNonSystemMessageChainHash != remoteNonSystemMessageChainHash) {
            syncBroadcastMessages(false, getCurrentBroadcastMessageInfoList, getBroadcastMessage, writeBroadcastMessage)
        }
    }

    private fun syncBroadcastMessages(
        systemMessage: Boolean,
        getCurrentBroadcastMessageInfoList: Function<Boolean, BroadcastMessageInfoListPBO>,
        getBroadcastMessage: Function<String, BroadcastMessagePBO>,
        writeBroadcastMessage: Consumer<BroadcastMessagePBO>
    ) {
        val localMessageList = broadcastMessageService.getAllMessages(systemMessage)
        val remoteMessageInfoList = getCurrentBroadcastMessageInfoList.apply(systemMessage).messagesList
        var sentMessages = 0
        var receivedMessages = 0

        val allMessageSet = HashSet<BroadcastMessageInfoPBO>()
        val localMessageMap = HashMap<String, BroadcastMessage>()
        val remoteMessageInfoMap = HashMap<String, BroadcastMessageInfoPBO>()

        remoteMessageInfoList.forEach { remoteMessageInfoMap[it.id] = it }
        allMessageSet.addAll(remoteMessageInfoList)

        localMessageList.forEach { localMessageMap[it.id] = it }
        allMessageSet.addAll(localMessageList.map { BroadcastMessageInfoPBO.newBuilder().setId(it.id).setCreated(it.created).build() })

        var allMessages = allMessageSet.toList()

        if(systemMessage) {
            allMessages = allMessages.sortedBy { it.created }
        } else {
            allMessages = allMessages.sortedByDescending { it.created }
        }

        for (msg in allMessages) {
            val local = localMessageMap.containsKey(msg.id)
            val remote = remoteMessageInfoMap.containsKey(msg.id)

            if(local == remote) {
                continue
            } else if (local) {
                writeBroadcastMessage.accept(localMessageMap[msg.id]!!.toPBO())
                sentMessages++
            } else {
                broadcastMessageService.handleBroadcastMessageReceived(getBroadcastMessage.apply(remoteMessageInfoMap[msg.id]!!.id).toDBO(), context)
                receivedMessages++
            }
        }

        Log.d(this.javaClass.name, "Finished syncBroadcastMessages Received: $receivedMessages Sent: $sentMessages")

        Intent().also { intent ->
            intent.setAction("com.strobel.emercast.NEW_BROADCAST_MESSAGE")
            context.sendBroadcast(intent)
        }
    }
}