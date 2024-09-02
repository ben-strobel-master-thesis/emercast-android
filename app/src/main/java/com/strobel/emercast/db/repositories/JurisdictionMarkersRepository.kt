package com.strobel.emercast.db.repositories

import android.content.ContentValues
import android.database.Cursor
import android.provider.BaseColumns
import com.strobel.emercast.db.EmercastDbHelper
import com.strobel.emercast.db.models.Authority
import com.strobel.emercast.db.models.JurisdictionMarker

class JurisdictionMarkersRepository(private val dbHelper: EmercastDbHelper) {

    fun newRow(
        authorityId: String,
        authorityCreated: Long,
        latitude: Float,
        longitude: Float,
        kind: String,
        radiusMeters: Int
    ): Long {
        val db = this.dbHelper.writableDatabase

        val values = ContentValues().apply {
            put(EmercastDbHelper.Companion.JurisdictionMarkerEntry.COLUMN_NAME_AUTHORITY_ID, authorityId)
            put(EmercastDbHelper.Companion.JurisdictionMarkerEntry.COLUMN_NAME_AUTHORITY_CREATED, authorityCreated)
            put(EmercastDbHelper.Companion.JurisdictionMarkerEntry.COLUMN_NAME_LATITUDE, latitude)
            put(EmercastDbHelper.Companion.JurisdictionMarkerEntry.COLUMN_NAME_LONGITUDE, longitude)
            put(EmercastDbHelper.Companion.JurisdictionMarkerEntry.COLUMN_NAME_KIND, kind)
            put(EmercastDbHelper.Companion.JurisdictionMarkerEntry.COLUMN_NAME_RADIUS_METERS, radiusMeters)
        }

        return db.insert(EmercastDbHelper.Companion.JurisdictionMarkerEntry.TABLE_NAME, null, values)
    }

    fun getForAuthorityDigest(authorityId: String, authorityCreated: Long): String {
        return getForAuthority(authorityId, authorityCreated).map { markerToString(it) }.joinToString("")
    }

    private fun getForAuthority(authorityId: String, authorityCreated: Long): List<JurisdictionMarker> {
        val db = this.dbHelper.readableDatabase

        val cursor = db.query(
            EmercastDbHelper.Companion.JurisdictionMarkerEntry.TABLE_NAME,
            null,
            "${EmercastDbHelper.Companion.JurisdictionMarkerEntry.COLUMN_NAME_AUTHORITY_ID} = ? and " +
            "${EmercastDbHelper.Companion.JurisdictionMarkerEntry.COLUMN_NAME_AUTHORITY_CREATED} = ? and ",
            arrayOf(authorityId, ""+authorityCreated),
            null,
            null,
            "${BaseColumns._ID} ASC"
        )

        val markers = mutableListOf<JurisdictionMarker>()

        while(cursor.moveToNext()) {
            markers.add(getFromCursor(cursor)!!)
        }
        cursor.close()
        return markers
    }

    private fun markerToString(marker: JurisdictionMarker): String {
        return "JurisdictionMarker{" +
                "kind=" + marker.kind +
                ", latitude=" + marker.latitude +
                ", longitude=" + marker.longitude +
                ", radiusMeters=" + marker.radiusMeters +
                '}';
    }

    private fun getFromCursor(cursor: Cursor): JurisdictionMarker? {
        if(cursor.isClosed) return null
        return JurisdictionMarker(
            cursor.getString(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.JurisdictionMarkerEntry.COLUMN_NAME_AUTHORITY_ID)),
            cursor.getLong(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.JurisdictionMarkerEntry.COLUMN_NAME_AUTHORITY_CREATED)),
            cursor.getFloat(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.JurisdictionMarkerEntry.COLUMN_NAME_LATITUDE)),
            cursor.getFloat(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.JurisdictionMarkerEntry.COLUMN_NAME_LONGITUDE)),
            cursor.getString(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.JurisdictionMarkerEntry.COLUMN_NAME_KIND)),
            cursor.getInt(cursor.getColumnIndexOrThrow(EmercastDbHelper.Companion.JurisdictionMarkerEntry.COLUMN_NAME_RADIUS_METERS))
        )
    }

    fun deleteForAuthority(
        authority: Authority
    ): Boolean {
        val db = this.dbHelper.writableDatabase
        var selection = "${EmercastDbHelper.Companion.JurisdictionMarkerEntry.COLUMN_NAME_AUTHORITY_ID} = ?"
        val selectionArgs = arrayOf(authority.id)
        val deletedRows = db.delete(EmercastDbHelper.Companion.JurisdictionMarkerEntry.TABLE_NAME, selection, selectionArgs)
        return deletedRows > 0
    }
}