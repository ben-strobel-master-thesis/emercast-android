package com.strobel.emercast.db.repositories

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.getLongOrNull
import com.strobel.emercast.db.EmercastDbHelper
import com.strobel.emercast.db.models.BroadcastMessage
import java.security.MessageDigest
import java.time.Instant
import kotlin.io.encoding.Base64

// TODO Split into repo & service
class BroadcastMessagesRepository(private val dbHelper: EmercastDbHelper) {

    fun newRow(
        id: String,
        created: Long,
        systemMessage: Boolean,
        forwardUntil: Long,
        latitude: Float,
        longitude: Float,
        radius: Int,
        category: String,
        severity: Int,
        title: String,
        content: String,
        issuedAuthorityId: String,
        issuerSignature: String,

        received: Long,
        directlyReceived: Boolean,
        systemMessageRegardingAuthority: String
    ): BroadcastMessage {
        val db = this.dbHelper.writableDatabase

        val values = ContentValues().apply {
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_ID, id)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_CREATED, created)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_SYSTEM_MESSAGE, systemMessage)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_FORWARD_UNTIL, forwardUntil)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_LATITUDE, latitude)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_LONGITUDE, longitude)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_RADIUS, radius)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_CATEGORY, category)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_SEVERITY, severity)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_TITLE, title)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_MESSAGE, content)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_ISSUED_AUTHORITY_ID, issuedAuthorityId)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_ISSUER_SIGNATURE, issuerSignature)

            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_RECEIVED, received)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_DIRECTLY_RECEIVED, directlyReceived)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_SYSTEM_MESSAGE_REGARDING_AUTHORITY, systemMessageRegardingAuthority)
        }

        db.insert(EmercastDbHelper.Companion.BroadcastMessageEntry.TABLE_NAME, null, values)

        return getById(db, id)!!
    }

    fun deleteMessage(message: BroadcastMessage): Boolean {
        val db = this.dbHelper.writableDatabase
        val selection = "${EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_ID} = ?"
        val selectionArgs = arrayOf(message.id)
        val deletedRows = db.delete(EmercastDbHelper.Companion.BroadcastMessageEntry.TABLE_NAME, selection, selectionArgs)
        return deletedRows > 0
    }

    fun getAllMessages(systemMessage: Boolean): List<BroadcastMessage> {
        val db = this.dbHelper.readableDatabase

        val now = Instant.now().epochSecond

        val selection = "${EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_FORWARD_UNTIL} > ? and " +
                "(${EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_FORWARD_UNTIL_OVERRIDE} is null or " +
                "${EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_FORWARD_UNTIL_OVERRIDE} > ?) and " +
                "${EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_SYSTEM_MESSAGE} = ?"
        val selectionArgs = arrayOf(""+now, ""+now, ""+systemMessage)
        val sortOrder = "${EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_CREATED} DESC"

        val cursor = db.query(
            EmercastDbHelper.Companion.BroadcastMessageEntry.TABLE_NAME,
            null,
            selection,
            selectionArgs,
            null,
            null,
            sortOrder
        )

        val messages = mutableListOf<BroadcastMessage>()

        while(cursor.moveToNext()) {
            messages.add(getFromCursor(cursor)!!)
        }
        cursor.close()
        return messages
    }

    private fun getById(db: SQLiteDatabase, id: String): BroadcastMessage? {
        val cursor = db.query(
            EmercastDbHelper.Companion.BroadcastMessageEntry.TABLE_NAME,
            null,
            "${EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_ID} = ?",
            arrayOf(id),
            null,
            null,
            null
        )
        val result = getFromCursor(cursor)
        cursor.close()
        return result
    }

    private fun getFromCursor(cursor: Cursor): BroadcastMessage? {
        if(cursor.isClosed || cursor.count == 0) return null
        return BroadcastMessage(
            cursor.getString(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_ID)),
            cursor.getLong(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_CREATED)),
            cursor.getInt(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_SYSTEM_MESSAGE)) == 1,
            cursor.getLong(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_FORWARD_UNTIL)),
            cursor.getFloat(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_LATITUDE)),
            cursor.getFloat(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_LONGITUDE)),
            cursor.getInt(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_RADIUS)),
            cursor.getString(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_CATEGORY)),
            cursor.getInt(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_SEVERITY)),
            cursor.getString(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_TITLE)),
            cursor.getString(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_MESSAGE)),
            cursor.getString(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_ISSUED_AUTHORITY_ID)),
            cursor.getString(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_ISSUER_SIGNATURE)),

            cursor.getLong(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_RECEIVED)),
            cursor.getInt(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_DIRECTLY_RECEIVED)) == 1,
            cursor.getString(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_SYSTEM_MESSAGE_REGARDING_AUTHORITY)),

            cursor.getLongOrNull(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_FORWARD_UNTIL_OVERRIDE)),
        )
    }

    fun getMessageHashForBLEAdvertisement(): ByteArray {
        return getMessageHash(false).asList().subList(0, 16).toByteArray();
    }

    private fun getMessageHash(systemMessage: Boolean): ByteArray {
        val builder = StringBuilder()
        val messages = getAllMessages(systemMessage)

        // Messages are already ordered by created timestamp (from repo)
        messages.forEach{ m ->
            builder.append(m.issuerSignature)
        }

        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(builder.toString().toByteArray(Charsets.UTF_8))
    }

    fun getMessageHashBase64(systemMessage: Boolean): String {
        return java.util.Base64.getEncoder().encodeToString(getMessageHash(systemMessage))
    }
}