package com.strobel.emercast.db.repositories

import android.content.ContentValues
import com.strobel.emercast.db.EmercastDbHelper

class AuthoritiesRepository(private val dbHelper: EmercastDbHelper) {

    fun handleNewSystemAuthorityIssuedMessage(broadcastMessageId: String) {

    }

    fun handleNewSystemAuthorityRevokedMessage(broadcastMessageId: String) {

    }

    private fun newRow(
        id: String,
        created: Int,
        createdBy: String,
        publicName: String,
        validUntil: Int,
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

    // Return null if extraction fails, authority does not exist or signature is not valid at the given timestamp
    fun extractSignedContentOfAuthority(authorityId: String, authorityValidAt: Int, signedContent: String): String? {
        // TODO
        return null
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