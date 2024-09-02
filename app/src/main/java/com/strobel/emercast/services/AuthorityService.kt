package com.strobel.emercast.services

import com.strobel.emercast.db.EmercastDbHelper
import com.strobel.emercast.db.models.Authority
import com.strobel.emercast.db.models.BroadcastMessage
import com.strobel.emercast.db.repositories.AuthoritiesRepository
import com.strobel.emercast.db.repositories.BroadcastMessagesRepository
import com.strobel.emercast.protobuf.SystemBroadcastMessageAuthorityIssuedPayloadPBO
import com.strobel.emercast.protobuf.SystemBroadcastMessageAuthorityRevokedPayloadPBO
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.PublicKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher

class AuthorityService(dbHelper: EmercastDbHelper) {

    private val authoritiesRepository = AuthoritiesRepository(dbHelper)

    fun handleNewSystemAuthorityIssuedMessage(broadcastMessage: BroadcastMessage, pinnedRootAuthorityPublicKey: String): Boolean {
        val payload = SystemBroadcastMessageAuthorityIssuedPayloadPBO.parseFrom(Base64.getDecoder().decode(broadcastMessage.content))
        broadcastMessage.systemMessageRegardingAuthority = payload.authority.id

        val authority = Authority(
            payload.authority.id,
            payload.authority.created,
            payload.authority.createdBy,
            payload.authority.publicName,
            payload.authority.keyPairValidUntil,
            payload.authority.publicKeyBase64,
            null
        )

        if(authority.id == ROOT_AUTHORITY_UUID && authority.publicKeyBase64 != pinnedRootAuthorityPublicKey) return false

        if(broadcastMessage.issuedAuthorityId != authority.createdBy) return false
        authoritiesRepository.newRow(authority)
        return true
    }

    fun handleNewSystemAuthorityRevokedMessage(broadcastMessage: BroadcastMessage, broadcastMessageService: BroadcastMessageService): Boolean {
        val payload = SystemBroadcastMessageAuthorityRevokedPayloadPBO.parseFrom(Base64.getDecoder().decode(broadcastMessage.content))
        broadcastMessage.systemMessageRegardingAuthority = payload.authorityId

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

    fun doesAuthorityExist(authorityId: String, validAt: Long): Boolean {
        return authoritiesRepository.getAuthority(authorityId, validAt) != null
    }

    // The signed content must be provided within the message that is to be verified
    // The plain content must be calculated using the data in the message that is to be verified
    private fun verifyContentWasSignedByAuthority(signerAuthority: Authority, signedContent: String, plainContent: ByteArray): Boolean {
        val md = MessageDigest.getInstance("SHA-256")
        val plainHashedContent =  md.digest(plainContent)

        val signedBytes = Base64.getDecoder().decode(signedContent)
        val cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, getPublicKey(signerAuthority))
        var decryptedBytes = cipher.doFinal(signedBytes)
        if(decryptedBytes.size < 255) return false
        decryptedBytes = decryptedBytes.takeLast(32).toByteArray()
        return decryptedBytes.contentEquals(plainHashedContent)
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

    companion object {
        const val ROOT_AUTHORITY_UUID = "00000000-0000-0000-0000-000000000000"
    }
}