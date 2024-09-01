package com.strobel.emercast.services

import com.strobel.emercast.db.EmercastDbHelper
import com.strobel.emercast.db.models.BroadcastMessage
import com.strobel.emercast.db.repositories.BroadcastMessagesRepository

class BroadcastMessageService(private val dbHelper: EmercastDbHelper) {

    private val authorityService = AuthorityService(dbHelper)
    private val broadcastMessagesRepository = BroadcastMessagesRepository(dbHelper)

    fun handleBroadcastMessageReceived(broadcastMessage: BroadcastMessage) {
        if(!authorityService.verifyBroadcastMessage(broadcastMessage)) return

        if(broadcastMessage.systemMessage) {
            val title = broadcastMessage.title
            if(title == "AUTHORITY_ISSUED") {
                if(!authorityService.handleNewSystemAuthorityIssuedMessage(broadcastMessage)) return
            } else if (title == "AUTHORITY_REVOKED") {
                if(!authorityService.handleNewSystemAuthorityRevokedMessage(broadcastMessage, this)) return
            }
        }

        broadcastMessagesRepository.newRow(broadcastMessage)
    }

    fun overrideForwardUntil(broadcastMessageId: String, newOverrideForwardUntil: Long?): Boolean {
        val broadcastMessage = broadcastMessagesRepository.findById(broadcastMessageId)?: return false

        broadcastMessage.forwardUntilOverride = newOverrideForwardUntil
        broadcastMessagesRepository.updateForwardUntilOverride(broadcastMessage.id, newOverrideForwardUntil)

        return true
    }
}