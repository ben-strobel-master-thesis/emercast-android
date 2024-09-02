package com.strobel.emercast.services

import android.content.Context
import android.widget.Toast
import com.openapi.gen.android.api.DefaultApi
import com.openapi.gen.android.dto.BroadcastMessageDTO
import com.strobel.emercast.R
import com.strobel.emercast.db.EmercastDbHelper
import com.strobel.emercast.db.models.BroadcastMessage
import com.strobel.emercast.db.repositories.BroadcastMessagesRepository
import com.strobel.emercast.lib.Pageable
import java.time.Instant

class BroadcastMessageService(private val dbHelper: EmercastDbHelper) {

    private val authorityService = AuthorityService(dbHelper)
    private val broadcastMessagesRepository = BroadcastMessagesRepository(dbHelper)

    suspend fun pullBroadcastMessagesFromServer(context: Context) {
        try {
            val api = DefaultApi(context, basePath = context.resources.getString(R.string.api_url))

            val broadcastMessageHashSystem = broadcastMessagesRepository.getMessageHashBase64(true)
            val broadcastMessageHashNonSystem = broadcastMessagesRepository.getMessageHashBase64(false)

            var response = api.getBroadcastMessageChainHash(true)
            if(response?.hash != broadcastMessageHashSystem) {
                pullPaginatedBroadcastMessagesFromServer(true, api, context)
            }

            response = api.getBroadcastMessageChainHash(false)
            if(response?.hash != broadcastMessageHashNonSystem) {
                pullPaginatedBroadcastMessagesFromServer(false, api, context)
            }
        } catch (ex: Exception) {
            Toast.makeText(context, "Failed to pull messages from server", Toast.LENGTH_SHORT).show()
            ex.printStackTrace()
        }
    }

    private suspend fun pullPaginatedBroadcastMessagesFromServer(systemMessage: Boolean, api: DefaultApi, context: Context) {
        // TODO Optimize:
        //  Non System Messages: (sync order newest -> oldest) Abort once hash is equal
        //  System Messages: (sync order oldest -> newest) Start at newest authority

        var lastPageItemCount = 0
        var first = true
        var pageable = Pageable(0, 20)

        while (lastPageItemCount >= pageable.pageSize || first) {
            val items = api.getBroadcastMessagesPage(pageable.page, pageable.pageSize, systemMessage).orEmpty()

            for (msg in items) {
                handleBroadcastMessageReceived(msg.toDBO(Instant.now().epochSecond, true), context)
            }

            lastPageItemCount = items.size
            pageable = Pageable(pageable.page+1, pageable.pageSize)
            first = false
        }
    }

    fun handleBroadcastMessageReceived(broadcastMessage: BroadcastMessage, context: Context) {
        if(broadcastMessagesRepository.findById(broadcastMessage.id) != null) return

        if(broadcastMessage.issuedAuthorityId == AuthorityService.ROOT_AUTHORITY_UUID) {
            if(authorityService.doesAuthorityExist(AuthorityService.ROOT_AUTHORITY_UUID, broadcastMessage.created)) {
                if(!authorityService.verifyBroadcastMessage(broadcastMessage)) return
            }
        } else {
            if(!authorityService.verifyBroadcastMessage(broadcastMessage)) return
        }

        if(broadcastMessage.systemMessage) {
            val title = broadcastMessage.title
            if(title == "AUTHORITY_ISSUED") {
                if(!authorityService.handleNewSystemAuthorityIssuedMessage(broadcastMessage, context.resources.getString(R.string.pinned_root_authority_public_key))) return
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

    fun BroadcastMessageDTO.toDBO(received: Long, directlyReceived: Boolean): BroadcastMessage {
        return BroadcastMessage(
            this.id.toString(),
            this.created,
            this.systemMessage,
            this.forwardUntil,
            this.latitude,
            this.longitude,
            this.radius,
            this.category,
            this.severity,
            this.title,
            this.message,

            this.issuedAuthority.toString(),
            this.issuerSignature,

            received,
            directlyReceived,
            null,

            null
        )
    }
}