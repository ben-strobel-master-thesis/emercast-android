package com.strobel.emercast.db.repositories

import android.content.ContentValues
import android.database.Cursor
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import com.strobel.emercast.db.EmercastDbHelper
import com.strobel.emercast.db.models.Authority
import com.strobel.emercast.db.models.BroadcastMessage
import com.strobel.emercast.protobuf.SystemBroadcastMessageAuthorityIssuedPayloadPBO
import java.util.Base64


// TODO Split into repo & service
class AuthoritiesRepository(private val dbHelper: EmercastDbHelper) {

    fun newRow(authority: Authority): Long {
        val db = this.dbHelper.writableDatabase

        val values = ContentValues().apply {
            put(EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_ID, authority.id)
            put(EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_CREATED, authority.created)
            put(EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_CREATED_BY, authority.createdBy)
            put(EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_PUBLIC_NAME, authority.publicName)
            put(EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_VALID_UNTIL, authority.validUntil)
            put(EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_PUBLIC_KEY_BASE64, authority.publicKeyBase64)
        }

        return db.insert(EmercastDbHelper.Companion.AuthorityEntry.TABLE_NAME, null, values)
    }

    fun getAuthority(authorityId: String, authorityValidAt: Long): Authority? {
        val db = this.dbHelper.readableDatabase

        val selection = "${EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_ID} = ? and " +
            "(${EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_REVOKED_AFTER} is null or " +
            "${EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_REVOKED_AFTER} > ?) and " +
            "${EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_VALID_UNTIL} > ? and " +
            "${EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_CREATED} < ?"

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

    fun updateRevokedAfter(authorityId: String, revokedAfter: Long?) {
        val db = this.dbHelper.writableDatabase

        val values = ContentValues().apply {
            put(EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_REVOKED_AFTER, revokedAfter)
        }

        db.update(
            EmercastDbHelper.Companion.AuthorityEntry.TABLE_NAME,
            values,
            "${EmercastDbHelper.Companion.AuthorityEntry.COLUMN_NAME_ID} = ?",
            arrayOf(authorityId)
        )
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

    companion object {
        // TODO This should be extracted into a shared library between server & clients
        fun getMessageBytesForDigest(db: EmercastDbHelper, authority: Authority): ByteArray {
            val jurisdictionMarkersRepository = JurisdictionMarkersRepository(db)

            val builder = StringBuilder()
            builder.append(authority.created)
            builder.append(authority.createdBy)
            builder.append(authority.publicName)
            builder.append(jurisdictionMarkersRepository.getForAuthorityDigest(authority.id, authority.created))
            builder.append(authority.validUntil)
            builder.append(authority.publicKeyBase64)
            return builder.toString().toByteArray(Charsets.UTF_8)
        }
    }
}