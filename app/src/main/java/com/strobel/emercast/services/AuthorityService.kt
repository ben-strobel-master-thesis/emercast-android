package com.strobel.emercast.services

import com.strobel.emercast.db.EmercastDbHelper
import com.strobel.emercast.db.models.Authority
import com.strobel.emercast.db.models.BroadcastMessage
import com.strobel.emercast.db.repositories.AuthoritiesRepository
import com.strobel.emercast.db.repositories.BroadcastMessagesRepository
import com.strobel.emercast.protobuf.SystemBroadcastMessageAuthorityIssuedPayloadPBO
import com.strobel.emercast.protobuf.SystemBroadcastMessageAuthorityRevokedPayloadPBO
import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.PublicKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher

class AuthorityService(dbHelper: EmercastDbHelper) {

    private val authoritiesRepository = AuthoritiesRepository(dbHelper)

    fun handleNewSystemAuthorityIssuedMessage(broadcastMessage: BroadcastMessage): Boolean {
        val payload = SystemBroadcastMessageAuthorityIssuedPayloadPBO.parseFrom(Base64.getDecoder().decode(broadcastMessage.content))
        val authority = Authority(
            payload.authority.id,
            payload.authority.created,
            payload.authority.createdBy,
            payload.authority.publicName,
            payload.authority.keyPairValidUntil,
            payload.authority.publicKeyBase64,
            null
        )

        if(broadcastMessage.issuedAuthorityId != authority.createdBy) return false
        authoritiesRepository.newRow(authority)
        return true
    }

    fun handleNewSystemAuthorityRevokedMessage(broadcastMessage: BroadcastMessage, broadcastMessageService: BroadcastMessageService): Boolean {
        val payload = SystemBroadcastMessageAuthorityRevokedPayloadPBO.parseFrom(Base64.getDecoder().decode(broadcastMessage.content))

        val authority = authoritiesRepository.getAuthority(payload.authorityId, payload.revokedDate)?: return false
        broadcastMessageService.overrideForwardUntil(authority.id, payload.canBeDeletedAt)
        authoritiesRepository.updateRevokedAfter(authority.id, payload.revokedDate)

        return true
    }

    fun verifyBroadcastMessage(broadcastMessage: BroadcastMessage): Boolean {
        val parentAuthority = authoritiesRepository.getAuthority(broadcastMessage.issuedAuthorityId,  broadcastMessage.created) ?: return false

        return verifyContentWasSignedByAuthority(
            parentAuthority,
            broadcastMessage.issuerSignature,
            BroadcastMessagesRepository.getMessageBytesForDigest(broadcastMessage)
        )
    }

    // The signed content must be provided within the message that is to be verified
    // The plain content must be calculated using the data in the message that is to be verified
    private fun verifyContentWasSignedByAuthority(signerAuthority: Authority, signedContent: String, plainContent: ByteArray): Boolean {
        val signedBytes = Base64.getDecoder().decode(signedContent)
        val cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, getPublicKey(signerAuthority))
        val decryptedBytes = cipher.doFinal(signedBytes)
        return decryptedBytes.contentEquals(plainContent)
    }

    private fun getPublicKey(authority: Authority): PublicKey {
        val decoded = Base64.getDecoder().decode(authority.publicKeyBase64)
        try {
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKeySpec = X509EncodedKeySpec(decoded)
            return keyFactory.generatePublic(publicKeySpec)
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e)
        } catch (e: InvalidKeySpecException) {
            throw RuntimeException(e)
        }
    }
}