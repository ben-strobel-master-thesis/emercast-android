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

    fun newRow(broadcastMessage: BroadcastMessage) {
        val db = this.dbHelper.writableDatabase

        val values = ContentValues().apply {
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_ID, broadcastMessage.id)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_CREATED, broadcastMessage.created)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_SYSTEM_MESSAGE, broadcastMessage.systemMessage)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_FORWARD_UNTIL, broadcastMessage.forwardUntil)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_LATITUDE, broadcastMessage.latitude)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_LONGITUDE, broadcastMessage.longitude)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_RADIUS, broadcastMessage.radius)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_CATEGORY, broadcastMessage.category)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_SEVERITY, broadcastMessage.severity)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_TITLE, broadcastMessage.title)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_MESSAGE, broadcastMessage.content)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_ISSUED_AUTHORITY_ID, broadcastMessage.issuedAuthorityId)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_ISSUER_SIGNATURE, broadcastMessage.issuerSignature)

            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_RECEIVED, broadcastMessage.received)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_DIRECTLY_RECEIVED, broadcastMessage.directlyReceived)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_SYSTEM_MESSAGE_REGARDING_AUTHORITY, broadcastMessage.systemMessageRegardingAuthority)
        }

        db.insert(EmercastDbHelper.Companion.BroadcastMessageEntry.TABLE_NAME, null, values)
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

    fun findById(id: String): BroadcastMessage? {
        val db = this.dbHelper.readableDatabase

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

    fun updateForwardUntilOverride(broadcastMessageId: String, forwardUntilOverride: Long?) {
        val db = this.dbHelper.writableDatabase

        val values = ContentValues().apply {
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_FORWARD_UNTIL_OVERRIDE, forwardUntilOverride)
        }

        db.update(
            EmercastDbHelper.Companion.BroadcastMessageEntry.TABLE_NAME,
            values,
            "${EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_ID} = ?",
            arrayOf(broadcastMessageId)
        )
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

    companion object {
        fun getMessageBytesForDigest(broadcastMessage: BroadcastMessage): ByteArray {
            val builder = StringBuilder()
            builder.append(broadcastMessage.created)
            builder.append(broadcastMessage.issuedAuthorityId)
            builder.append(broadcastMessage.systemMessage)
            builder.append(broadcastMessage.forwardUntil)
            builder.append(broadcastMessage.latitude)
            builder.append(broadcastMessage.longitude)
            builder.append(broadcastMessage.radius)
            builder.append(broadcastMessage.category)
            builder.append(broadcastMessage.severity)
            builder.append(broadcastMessage.title)
            builder.append(broadcastMessage.content)
            return builder.toString().toByteArray(Charsets.UTF_8)
        }
    }
}