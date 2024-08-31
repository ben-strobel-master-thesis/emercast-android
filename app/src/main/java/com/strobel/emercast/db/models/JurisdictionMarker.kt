package com.strobel.emercast.db.models

data class JurisdictionMarker (
    val authorityId: String,
    val authorityCreated: Long,
    val latitude: Float,
    val longitude: Float,
    val kind: String,
    val radiusMeters: Int
)