package com.strobel.emercast.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns

// https://developer.android.com/training/data-storage/sqlite#:~:text=Just%20like%20files%20that%20you,other%20apps%20or%20the%20user
class FeedReaderDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

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
        const val DATABASE_NAME = "FeedReader.db"

        object FeedEntry : BaseColumns {
            const val TABLE_NAME = "BroadcastMessage"
            const val COLUMN_NAME_ID = "id"
            const val COLUMN_NAME_CREATED = "created"
            const val COLUMN_NAME_MODIFIED = "modified"
            const val COLUMN_NAME_RECEIVED = "received"
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
            "CREATE TABLE ${FeedEntry.TABLE_NAME} (" +
                    "${BaseColumns._ID} INTEGER PRIMARY KEY," +
                    "${FeedEntry.COLUMN_NAME_ID} TEXT," +
                    "${FeedEntry.COLUMN_NAME_CREATED} INTEGER," +
                    "${FeedEntry.COLUMN_NAME_MODIFIED} INTEGER," +
                    "${FeedEntry.COLUMN_NAME_RECEIVED} INTEGER," +
                    "${FeedEntry.COLUMN_NAME_FORWARD_UNTIL} INTEGER," +
                    "${FeedEntry.COLUMN_NAME_LATITUDE} REAL," +
                    "${FeedEntry.COLUMN_NAME_LONGITUDE} REAL," +
                    "${FeedEntry.COLUMN_NAME_RADIUS} INTEGER," +
                    "${FeedEntry.COLUMN_NAME_CATEGORY} TEXT," +
                    "${FeedEntry.COLUMN_NAME_SEVERITY} INTEGER," +
                    "${FeedEntry.COLUMN_NAME_TITLE} TEXT," +
                    "${FeedEntry.COLUMN_NAME_MESSAGE} TEXT)"

        private const val SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS ${FeedEntry.TABLE_NAME}"
    }
}