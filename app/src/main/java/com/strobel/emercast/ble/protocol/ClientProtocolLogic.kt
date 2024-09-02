package com.strobel.emercast.ble.protocol

import android.content.Context
import com.strobel.emercast.db.EmercastDbHelper
import com.strobel.emercast.protobuf.BroadcastMessageInfoListPBO
import com.strobel.emercast.protobuf.BroadcastMessagePBO
import com.strobel.emercast.services.BroadcastMessageService
import com.strobel.emercast.services.BroadcastMessageService.Companion.toDBO
import com.strobel.emercast.services.BroadcastMessageService.Companion.toPBO
import java.util.function.Consumer
import java.util.function.Function

class ClientProtocolLogic(private val context: Context) {

    private val broadcastMessageService = BroadcastMessageService(EmercastDbHelper(context))

    // TODO Get and respect MTU
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
        var localMessageList = broadcastMessageService.getAllMessages(systemMessage)
        var remoteMessageInfoList = getCurrentBroadcastMessageInfoList.apply(systemMessage).messagesList

        if(systemMessage) {
            localMessageList = localMessageList.sortedBy { it.created }
           remoteMessageInfoList = remoteMessageInfoList.sortedBy { it.created }
        } else {
            localMessageList = localMessageList.sortedByDescending { it.created }
            remoteMessageInfoList = remoteMessageInfoList.sortedByDescending { it.created }
        }

        var localIndex = 0
        var remoteIndex = 0

        while (localIndex < localMessageList.size || remoteIndex < remoteMessageInfoList.size) {
            val localElement = localMessageList[localIndex]
            val remoteElement = remoteMessageInfoList[remoteIndex]

            if(localElement.created == remoteElement.created) {
                localIndex++
                remoteIndex++

                if(localElement.id != remoteElement.id) {
                    broadcastMessageService.handleBroadcastMessageReceived(getBroadcastMessage.apply(remoteElement.id).toDBO(), context)
                    writeBroadcastMessage.accept(localElement.toPBO())
                }

                continue
            } else if (localElement.created < remoteElement.created) {
                writeBroadcastMessage.accept(localElement.toPBO())
                localIndex++
            } else {
                broadcastMessageService.handleBroadcastMessageReceived(getBroadcastMessage.apply(remoteElement.id).toDBO(), context)
                remoteIndex++
            }
        }
    }
}