package com.strobel.emercast.db.models

data class Authority (
    val id: String,
    val created: Int,
    val createdBy: String,
    val publicName: String,
    val validUntil: Int,
    val publicKeyBase64: String,

    val revokedAfter: Int?
)