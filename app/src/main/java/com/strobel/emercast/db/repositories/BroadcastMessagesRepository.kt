package com.strobel.emercast.db.repositories

import android.content.ContentValues
import android.provider.BaseColumns
import com.strobel.emercast.db.EmercastDbHelper
import com.strobel.emercast.db.models.BroadcastMessage

class BroadcastMessagesRepository(val dbHelper: EmercastDbHelper) {

    fun newRow(
        id: String,
        created: Long,
        modified: Long,
        received: Long,
        directlyReceived: Int,
        forwardUntil: Long,
        latitude: Float,
        longitude: Float,
        radius: Int,
        category: String,
        severity: Int,
        title: String,
        content: String
    ): Long {
        val db = this.dbHelper.writableDatabase

        val values = ContentValues().apply {
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_ID, id)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_CREATED, created)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_MODIFIED, modified)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_RECEIVED, received)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_DIRECTLY_RECEIVED, directlyReceived)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_FORWARD_UNTIL, forwardUntil)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_LATITUDE, latitude)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_LONGITUDE, longitude)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_RADIUS, radius)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_CATEGORY, category)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_SEVERITY, severity)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_TITLE, title)
            put(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_MESSAGE, content)
        }

        return db.insert(EmercastDbHelper.Companion.BroadcastMessageEntry.TABLE_NAME, null, values)
    }

    fun deleteMessage(message: BroadcastMessage): Boolean {
        val db = this.dbHelper.writableDatabase
        val selection = "${EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_ID} = ?"
        val selectionArgs = arrayOf(message.id)
        val deletedRows = db.delete(EmercastDbHelper.Companion.BroadcastMessageEntry.TABLE_NAME, selection, selectionArgs)
        return deletedRows > 0
    }

    fun getAllMessages(): List<BroadcastMessage> {
        val db = this.dbHelper.readableDatabase

        val sortOrder = "${EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_CREATED} DESC"

        val cursor = db.query(
            EmercastDbHelper.Companion.BroadcastMessageEntry.TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            sortOrder
        )

        val messages = mutableListOf<BroadcastMessage>()

        while(cursor.moveToNext()) {
            messages.add(BroadcastMessage(
                cursor.getString(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_ID)),
                cursor.getLong(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_CREATED)),
                cursor.getLong(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_MODIFIED)),
                cursor.getLong(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_RECEIVED)),
                cursor.getInt(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_DIRECTLY_RECEIVED)),
                cursor.getLong(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_FORWARD_UNTIL)),
                cursor.getFloat(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_LATITUDE)),
                cursor.getFloat(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_LONGITUDE)),
                cursor.getInt(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_RADIUS)),
                cursor.getString(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_CATEGORY)),
                cursor.getInt(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_SEVERITY)),
                cursor.getString(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_TITLE)),
                cursor.getString(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.BroadcastMessageEntry.COLUMN_NAME_MESSAGE)),
            ))
        }
        cursor.close()
        return messages
    }
}