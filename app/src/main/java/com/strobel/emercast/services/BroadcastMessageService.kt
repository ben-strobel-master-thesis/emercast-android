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
import com.strobel.emercast.protobuf.BroadcastMessageInfoPBO
import com.strobel.emercast.protobuf.BroadcastMessagePBO
import java.time.Instant

class BroadcastMessageService(dbHelper: EmercastDbHelper) {

    private val authorityService = AuthorityService(dbHelper)
    private val broadcastMessagesRepository = BroadcastMessagesRepository(dbHelper)

    suspend fun pullBroadcastMessagesFromServer(context: Context) {
        try {
            val api = DefaultApi(context, basePath = context.resources.getString(R.string.api_url))

            val broadcastMessageHashSystem = broadcastMessagesRepository.getMessageChainHashBase64(true)
            val broadcastMessageHashNonSystem = broadcastMessagesRepository.getMessageChainHashBase64(false)

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
        //  For production: Optimize:
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

    fun findMessageById(id: String): BroadcastMessage? {
        return broadcastMessagesRepository.findById(id)
    }

    fun getAllMessages(systemMessage: Boolean): List<BroadcastMessage> {
        return broadcastMessagesRepository.getAllMessages(systemMessage)
    }

    fun getMessageChainHash(systemMessage: Boolean): String {
        return broadcastMessagesRepository.getMessageChainHashBase64(systemMessage)
    }

    companion object {
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

        fun BroadcastMessagePBO.toDBO(): BroadcastMessage {
            return BroadcastMessage(
                this.id,
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
                this.issuedAuthority,
                this.issuerSignature,

                Instant.now().epochSecond,
                false,
                null,

                null
            )
        }

        fun BroadcastMessage.toPBO(): BroadcastMessagePBO {
            return BroadcastMessagePBO
                .newBuilder()
                .setId(this.id)
                .setCreated(this.created)
                .setSystemMessage(this.systemMessage)
                .setForwardUntil(this.forwardUntil)
                .setLatitude(this.latitude)
                .setLongitude(this.longitude)
                .setRadius(this.radius)
                .setCategory(this.category)
                .setSeverity(this.severity)
                .setTitle(this.title)
                .setMessage(this.content)
                .setIssuedAuthority(this.issuedAuthorityId)
                .setIssuerSignature(this.issuerSignature)
                .build()
        }

        fun BroadcastMessage.toInfoPBO(): BroadcastMessageInfoPBO {
            return BroadcastMessageInfoPBO
                .newBuilder()
                .setId(this.id)
                .setCreated(this.created)
                .build()
        }
    }
}