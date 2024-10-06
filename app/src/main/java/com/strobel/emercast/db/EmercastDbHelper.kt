package com.strobel.emercast.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns

// https://developer.android.com/training/data-storage/sqlite#:~:text=Just%20like%20files%20that%20you,other%20apps%20or%20the%20user
class EmercastDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_BROADCAST_MESSAGES)
        db.execSQL(SQL_CREATE_JURISDICTION_MARKERS)
        db.execSQL(SQL_CREATE_AUTHORITIES)

        db.execSQL(SQL_CREATE_AUTHORITIES_INDEX)
        db.execSQL(SQL_CREATE_JURISDICTION_MARKERS_INDEX)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL(SQL_DELETE_BROADCAST_MESSAGES_TABLE)
        db.execSQL(SQL_DELETE_AUTHORITIES_TABLE)
        db.execSQL(SQL_DELETE_JURISDICTION_MARKERS_TABLE)
        db.execSQL(SQL_DELETE_CURRENT_LOCATIONS_TABLE)

        db.execSQL(SQL_CREATE_BROADCAST_MESSAGES)
        db.execSQL(SQL_CREATE_JURISDICTION_MARKERS)
        db.execSQL(SQL_CREATE_AUTHORITIES)

        db.execSQL(SQL_CREATE_AUTHORITIES_INDEX)
        db.execSQL(SQL_CREATE_JURISDICTION_MARKERS_INDEX)
        db.execSQL(SQL_CREATE_CURRENT_LOCATIONS)
        onCreate(db)
    }
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }
    companion object {
        // If you change the database schema, you must increment the database version.
        const val DATABASE_VERSION = 3
        const val DATABASE_NAME = "Emercast.db"

        object AuthorityEntry: BaseColumns {
            const val TABLE_NAME = "Authorities"
            const val COLUMN_NAME_ID = "id"
            const val COLUMN_NAME_CREATED = "created"
            const val COLUMN_NAME_CREATED_BY = "createdBy"
            const val COLUMN_NAME_PUBLIC_NAME = "publicName"
            const val COLUMN_NAME_VALID_UNTIL = "validUntil"
            const val COLUMN_NAME_PUBLIC_KEY_BASE64 = "publicKeyBase64"

            const val COLUMN_NAME_REVOKED_AFTER = "revokedAfter"
        }

        private const val SQL_CREATE_AUTHORITIES =
            "CREATE TABLE ${AuthorityEntry.TABLE_NAME} (" +
                    "${AuthorityEntry.COLUMN_NAME_ID} TEXT not null," +
                    "${AuthorityEntry.COLUMN_NAME_CREATED} INTEGER not null," +
                    "${AuthorityEntry.COLUMN_NAME_CREATED_BY} TEXT not null," +
                    "${AuthorityEntry.COLUMN_NAME_PUBLIC_NAME} TEXT not null," +
                    "${AuthorityEntry.COLUMN_NAME_VALID_UNTIL} INTEGER not null," +
                    "${AuthorityEntry.COLUMN_NAME_PUBLIC_KEY_BASE64} TEXT not null," +
                    "${AuthorityEntry.COLUMN_NAME_REVOKED_AFTER} INTEGER," +
                    "PRIMARY KEY (${AuthorityEntry.COLUMN_NAME_ID}, ${AuthorityEntry.COLUMN_NAME_CREATED}))"

        private const val SQL_CREATE_AUTHORITIES_INDEX =
            "CREATE INDEX authorities_id_idx ON ${AuthorityEntry.TABLE_NAME} (${AuthorityEntry.COLUMN_NAME_ID})";

        object JurisdictionMarkerEntry: BaseColumns {
            const val TABLE_NAME = "JurisdictionMarkers"
            const val COLUMN_NAME_AUTHORITY_ID = "authorityId"
            const val COLUMN_NAME_AUTHORITY_CREATED = "authorityCreated"
            const val COLUMN_NAME_LATITUDE = "latitude"
            const val COLUMN_NAME_LONGITUDE = "longitude"
            const val COLUMN_NAME_KIND = "kind"
            const val COLUMN_NAME_RADIUS_METERS = "radiusMeters"
        }

        private const val SQL_CREATE_JURISDICTION_MARKERS =
            "CREATE TABLE ${JurisdictionMarkerEntry.TABLE_NAME} (" +
                    "${BaseColumns._ID} INTEGER PRIMARY KEY," +
                    "${JurisdictionMarkerEntry.COLUMN_NAME_AUTHORITY_ID} TEXT not null," +
                    "${JurisdictionMarkerEntry.COLUMN_NAME_AUTHORITY_CREATED} INTEGER not null," +
                    "${JurisdictionMarkerEntry.COLUMN_NAME_LATITUDE} FLOAT not null," +
                    "${JurisdictionMarkerEntry.COLUMN_NAME_LONGITUDE} FLOAT not null," +
                    "${JurisdictionMarkerEntry.COLUMN_NAME_KIND} TEXT not null," +
                    "${JurisdictionMarkerEntry.COLUMN_NAME_RADIUS_METERS} INTEGER not null)"

        object CurrentLocationEntry: BaseColumns {
            const val TABLE_NAME = "CurrentLocations"
            const val COLUMN_NAME_ID = "id"
            const val COLUMN_NAME_LATITUDE = "latitude"
            const val COLUMN_NAME_LONGITUDE = "longitude"
            const val COLUMN_NAME_LAST_CHANGE = "longitude"
        }

        private const val SQL_CREATE_CURRENT_LOCATIONS =
            "CREATE TABLE ${CurrentLocationEntry.TABLE_NAME} (" +
                    "${BaseColumns._ID} INTEGER PRIMARY KEY," +
                    "${CurrentLocationEntry.COLUMN_NAME_ID} TEXT not null," +
                    "${CurrentLocationEntry.COLUMN_NAME_LATITUDE} FLOAT not null," +
                    "${CurrentLocationEntry.COLUMN_NAME_LONGITUDE} FLOAT not null," +
                    "${CurrentLocationEntry.COLUMN_NAME_LAST_CHANGE} INTEGER not null)"

        private const val SQL_CREATE_JURISDICTION_MARKERS_INDEX =
            "CREATE INDEX jurisdiction_marker_id_idx ON ${JurisdictionMarkerEntry.TABLE_NAME} (${JurisdictionMarkerEntry.COLUMN_NAME_AUTHORITY_ID}, ${JurisdictionMarkerEntry.COLUMN_NAME_AUTHORITY_CREATED})";

        object BroadcastMessageEntry : BaseColumns {
            const val TABLE_NAME = "BroadcastMessages"
            const val COLUMN_NAME_ID = "id"
            const val COLUMN_NAME_CREATED = "created"
            const val COLUMN_NAME_SYSTEM_MESSAGE = "systemMessage"
            const val COLUMN_NAME_FORWARD_UNTIL = "forwardUntil"
            const val COLUMN_NAME_LATITUDE = "latitude"
            const val COLUMN_NAME_LONGITUDE = "longitude"
            const val COLUMN_NAME_RADIUS = "radius"
            const val COLUMN_NAME_CATEGORY = "category"
            const val COLUMN_NAME_SEVERITY = "severity"
            const val COLUMN_NAME_TITLE = "title"
            const val COLUMN_NAME_MESSAGE = "message"
            const val COLUMN_NAME_ISSUED_AUTHORITY_ID = "issuedAuthorityId"
            const val COLUMN_NAME_ISSUER_SIGNATURE = "issuerSignature"

            // Client side only, not changing after initial creation
            const val COLUMN_NAME_RECEIVED = "received"
            const val COLUMN_NAME_DIRECTLY_RECEIVED = "directlyReceived"
            const val COLUMN_NAME_SYSTEM_MESSAGE_REGARDING_AUTHORITY = "systemMessageRegardingAuthority";

            // Client side only, might change (system authority issued messages are overwritten if authority is being revoked)
            const val COLUMN_NAME_FORWARD_UNTIL_OVERRIDE = "forwardUntilOverride";
        }

        private const val SQL_CREATE_BROADCAST_MESSAGES =
            "CREATE TABLE ${BroadcastMessageEntry.TABLE_NAME} (" +
                    "${BaseColumns._ID} INTEGER PRIMARY KEY," +
                    "${BroadcastMessageEntry.COLUMN_NAME_ID} TEXT not null," +
                    "${BroadcastMessageEntry.COLUMN_NAME_CREATED} INTEGER not null," +
                    "${BroadcastMessageEntry.COLUMN_NAME_SYSTEM_MESSAGE} INTEGER not null," +
                    "${BroadcastMessageEntry.COLUMN_NAME_FORWARD_UNTIL} INTEGER not null," +
                    "${BroadcastMessageEntry.COLUMN_NAME_LATITUDE} REAL not null," +
                    "${BroadcastMessageEntry.COLUMN_NAME_LONGITUDE} REAL not null," +
                    "${BroadcastMessageEntry.COLUMN_NAME_RADIUS} INTEGER not null," +
                    "${BroadcastMessageEntry.COLUMN_NAME_CATEGORY} TEXT not null," +
                    "${BroadcastMessageEntry.COLUMN_NAME_SEVERITY} INTEGER not null," +
                    "${BroadcastMessageEntry.COLUMN_NAME_TITLE} TEXT not null," +
                    "${BroadcastMessageEntry.COLUMN_NAME_MESSAGE} TEXT not null," +
                    "${BroadcastMessageEntry.COLUMN_NAME_ISSUED_AUTHORITY_ID} TEXT not null," +
                    "${BroadcastMessageEntry.COLUMN_NAME_ISSUER_SIGNATURE} TEXT not null," +

                    "${BroadcastMessageEntry.COLUMN_NAME_RECEIVED} INTEGER not null," +
                    "${BroadcastMessageEntry.COLUMN_NAME_DIRECTLY_RECEIVED} INTEGER not null," +
                    "${BroadcastMessageEntry.COLUMN_NAME_SYSTEM_MESSAGE_REGARDING_AUTHORITY} TEXT," +

                    "${BroadcastMessageEntry.COLUMN_NAME_FORWARD_UNTIL_OVERRIDE} INTEGER)"

        private const val SQL_DELETE_BROADCAST_MESSAGES_TABLE = "DROP TABLE IF EXISTS ${BroadcastMessageEntry.TABLE_NAME}"
        private const val SQL_DELETE_AUTHORITIES_TABLE = "DROP TABLE IF EXISTS ${AuthorityEntry.TABLE_NAME}"
        private const val SQL_DELETE_JURISDICTION_MARKERS_TABLE = "DROP TABLE IF EXISTS ${JurisdictionMarkerEntry.TABLE_NAME}"
        private const val SQL_DELETE_CURRENT_LOCATIONS_TABLE = "DROP TABLE IF EXISTS ${CurrentLocationEntry.TABLE_NAME}"
    }
}