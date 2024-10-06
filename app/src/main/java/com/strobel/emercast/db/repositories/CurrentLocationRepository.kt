package com.strobel.emercast.db.repositories

import android.content.ContentValues
import android.database.Cursor
import android.database.DatabaseUtils
import com.strobel.emercast.db.EmercastDbHelper
import java.time.Instant

class CurrentLocationRepository(private val dbHelper: EmercastDbHelper) {
    private final val singletonId = "00000000-0000-0000-0000-000000000000"

    private fun newRow(latitude: Float, longitude: Float): Long {
        val db = this.dbHelper.writableDatabase

        val values = ContentValues().apply {
            put(EmercastDbHelper.Companion.CurrentLocationEntry.COLUMN_NAME_ID, singletonId)
            put(EmercastDbHelper.Companion.CurrentLocationEntry.COLUMN_NAME_LATITUDE, latitude)
            put(EmercastDbHelper.Companion.CurrentLocationEntry.COLUMN_NAME_LONGITUDE, longitude)
            put(EmercastDbHelper.Companion.CurrentLocationEntry.COLUMN_NAME_LAST_CHANGE, Instant.now().epochSecond)
        }

        return db.insert(EmercastDbHelper.Companion.CurrentLocationEntry.TABLE_NAME, null, values)
    }

    private fun getFromCursor(cursor: Cursor): Pair<Float, Float>? {
        if(cursor.isClosed) return null
        return Pair(
            cursor.getFloat(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.CurrentLocationEntry.COLUMN_NAME_LATITUDE)),
            cursor.getFloat(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.CurrentLocationEntry.COLUMN_NAME_LONGITUDE))
        )
    }

    fun getCurrent(): Pair<Float, Float>? {
        val db = this.dbHelper.readableDatabase

        val cursor = db.query(
            EmercastDbHelper.Companion.CurrentLocationEntry.TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            null
        )

        if(!cursor.moveToFirst()) return null
        val currentLocation = getFromCursor(cursor)
        cursor.close()
        return currentLocation
    }

    fun update(latitude: Float, longitude: Float): Long {
        val db = this.dbHelper.writableDatabase

        var hasEntry = DatabaseUtils.queryNumEntries(db, EmercastDbHelper.Companion.CurrentLocationEntry.TABLE_NAME) > 0
        if(!hasEntry) {
            return newRow(latitude, longitude)
        } else {
            val values = ContentValues().apply {
                put(EmercastDbHelper.Companion.CurrentLocationEntry.COLUMN_NAME_LATITUDE, latitude)
                put(EmercastDbHelper.Companion.CurrentLocationEntry.COLUMN_NAME_LONGITUDE, longitude)
                put(EmercastDbHelper.Companion.CurrentLocationEntry.COLUMN_NAME_LAST_CHANGE, Instant.now().epochSecond)
            }

            return db.update(
                EmercastDbHelper.Companion.CurrentLocationEntry.TABLE_NAME,
                values,
                "${EmercastDbHelper.Companion.CurrentLocationEntry.COLUMN_NAME_ID} = ?",
                arrayOf(singletonId)
            ).toLong()
        }
    }
}