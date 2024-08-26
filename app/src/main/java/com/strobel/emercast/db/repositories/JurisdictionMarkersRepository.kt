package com.strobel.emercast.db.repositories

import android.content.ContentValues
import com.strobel.emercast.db.EmercastDbHelper
import com.strobel.emercast.db.models.Authority

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