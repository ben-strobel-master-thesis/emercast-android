package com.strobel.emercast.db.repositories

import android.content.ContentValues
import android.database.Cursor
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import com.strobel.emercast.db.EmercastDbHelper
import com.strobel.emercast.db.models.Authority
import com.strobel.emercast.db.models.BroadcastMessage
import com.strobel.emercast.protobuf.SystemBroadcastMessageAuthorityIssuedPayloadPBO
import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.PublicKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher


// TODO Split into repo & service
class AuthoritiesRepository(private val dbHelper: EmercastDbHelper) {

    fun handleNewSystemAuthorityIssuedMessage(broadcastMessage: BroadcastMessage) {
        val db = this.dbHelper.writableDatabase
        val parentAuthority = getAuthority(broadcastMessage.issuedAuthorityId,  broadcastMessage.created) ?: return

        val payload = SystemBroadcastMessageAuthorityIssuedPayloadPBO.parseFrom(Base64.getDecoder().decode(broadcastMessage.content))

        // verifyContentWasSignedByAuthority(parentAuthority, broadcastMessage.issuerSignature, getMessageBytesForDigest())

        // TODO
    }

    fun handleNewSystemAuthorityRevokedMessage(broadcastMessage: BroadcastMessage) {
        // TODO
    }

    private fun newRow(
        id: String,
        created: Long,
        createdBy: String,
        publicName: String,
        validUntil: Long,
        publicKeyBase64: String
    ): Long {
        val db = this.dbHelper.writableDatabase

        val values = ContentValues().apply {
            put(EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_ID, id)
            put(EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_CREATED, created)
            put(EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_CREATED_BY, createdBy)
            put(EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_PUBLIC_NAME, publicName)
            put(EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_VALID_UNTIL, validUntil)
            put(EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_PUBLIC_KEY_BASE64, publicKeyBase64)
        }

        return db.insert(EmercastDbHelper.Companion.AuthorityEntry.TABLE_NAME, null, values)
    }

    fun getAuthority(authorityId: String, authorityValidAt: Long): Authority? {
        val db = this.dbHelper.readableDatabase

        val selection = "${EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_ID} = ? and " +
                "(${EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_REVOKED_AFTER} is null or" +
                "${EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_REVOKED_AFTER} > ?) and"
                "${EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_VALID_UNTIL} > ? and"
                "${EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_CREATED} < ? and"
        val selectionArgs = arrayOf(authorityId, ""+authorityValidAt, ""+authorityValidAt, ""+authorityValidAt)
        val sortOrder = "${EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_CREATED} DESC"

        val cursor = db.query(
            EmercastDbHelper.Companion.AuthorityEntry.TABLE_NAME,
            null,
            selection,
            selectionArgs,
            null,
            null,
            sortOrder
        );

        val authority = getFromCursor(cursor)
        cursor.close()
        return authority
    }

    private fun getFromCursor(cursor: Cursor): Authority? {
        if(cursor.isClosed || cursor.count == 0) return null
        return Authority(
            cursor.getString(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_ID)),
            cursor.getLong(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_CREATED)),
            cursor.getString(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_CREATED_BY)),
            cursor.getString(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_PUBLIC_NAME)),
            cursor.getLong(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_VALID_UNTIL)),
            cursor.getString(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_PUBLIC_KEY_BASE64)),

            cursor.getLongOrNull(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_REVOKED_AFTER))
        )
    }

    // The signed content must be provided within the message that is to be verified
    // The plain content must be calculated using the data in the message that is to be verified
    fun verifyContentWasSignedByAuthority(signerAuthority: Authority, signedContent: String, plainContent: ByteArray): Boolean {
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

    // TODO This should be extracted into a shared library between server & clients
    private fun getMessageBytesForDigest(authority: Authority): ByteArray {
        val jurisdictionMarkersRepository = JurisdictionMarkersRepository(dbHelper)

        val builder = StringBuilder()
        builder.append(authority.created)
        builder.append(authority.createdBy)
        builder.append(authority.publicName)
        builder.append(jurisdictionMarkersRepository.getForAuthorityDigest(authority.id, authority.created))
        builder.append(authority.validUntil)
        builder.append(authority.publicKeyBase64)
        return builder.toString().toByteArray(Charsets.UTF_8)
    }

    fun delete(
        authorityId: String
    ): Boolean {
        val db = this.dbHelper.writableDatabase
        val selection = "${EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_ID} = ?"
        val selectionArgs = arrayOf(authorityId)
        val deletedRows = db.delete(EmercastDbHelper.Companion.AuthorityEntry.TABLE_NAME, selection, selectionArgs)
        return deletedRows > 0
    }
}