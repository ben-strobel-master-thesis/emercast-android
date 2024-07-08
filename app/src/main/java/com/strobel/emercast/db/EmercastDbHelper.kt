package com.strobel.emercast.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns

// https://developer.android.com/training/data-storage/sqlite#:~:text=Just%20like%20files%20that%20you,other%20apps%20or%20the%20user
class EmercastDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_BROADCAST_MESSAGES)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL(SQL_DELETE_ENTRIES)
        onCreate(db)
    }
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }
    companion object {
        // If you change the database schema, you must increment the database version.
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "Emercast.db"

        object BroadcastMessageEntry : BaseColumns {
            const val TABLE_NAME = "BroadcastMessage"
            const val COLUMN_NAME_ID = "id"
            const val COLUMN_NAME_CREATED = "created"
            const val COLUMN_NAME_MODIFIED = "modified"
            const val COLUMN_NAME_RECEIVED = "received"
            const val COLUMN_NAME_DIRECTLY_RECEIVED = "directlyReceived"
            const val COLUMN_NAME_FORWARD_UNTIL = "forwardUntil"
            const val COLUMN_NAME_LATITUDE = "latitude"
            const val COLUMN_NAME_LONGITUDE = "longitude"
            const val COLUMN_NAME_RADIUS = "radius"
            const val COLUMN_NAME_CATEGORY = "category"
            const val COLUMN_NAME_SEVERITY = "severity"
            const val COLUMN_NAME_TITLE = "title"
            const val COLUMN_NAME_MESSAGE = "message"
        }

        private const val SQL_CREATE_BROADCAST_MESSAGES =
            "CREATE TABLE ${BroadcastMessageEntry.TABLE_NAME} (" +
                    "${BaseColumns._ID} INTEGER PRIMARY KEY," +
                    "${BroadcastMessageEntry.COLUMN_NAME_ID} TEXT," +
                    "${BroadcastMessageEntry.COLUMN_NAME_CREATED} INTEGER," +
                    "${BroadcastMessageEntry.COLUMN_NAME_MODIFIED} INTEGER," +
                    "${BroadcastMessageEntry.COLUMN_NAME_RECEIVED} INTEGER," +
                    "${BroadcastMessageEntry.COLUMN_NAME_DIRECTLY_RECEIVED} INTEGER," +
                    "${BroadcastMessageEntry.COLUMN_NAME_FORWARD_UNTIL} INTEGER," +
                    "${BroadcastMessageEntry.COLUMN_NAME_LATITUDE} REAL," +
                    "${BroadcastMessageEntry.COLUMN_NAME_LONGITUDE} REAL," +
                    "${BroadcastMessageEntry.COLUMN_NAME_RADIUS} INTEGER," +
                    "${BroadcastMessageEntry.COLUMN_NAME_CATEGORY} TEXT," +
                    "${BroadcastMessageEntry.COLUMN_NAME_SEVERITY} INTEGER," +
                    "${BroadcastMessageEntry.COLUMN_NAME_TITLE} TEXT," +
                    "${BroadcastMessageEntry.COLUMN_NAME_MESSAGE} TEXT)"

        private const val SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS ${BroadcastMessageEntry.TABLE_NAME}"
    }
}