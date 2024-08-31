package com.strobel.emercast.db.models

data class Authority (
    val id: String,
    val created: Long,
    val createdBy: String,
    val publicName: String,
    val validUntil: Long,
    val publicKeyBase64: String,

    val revokedAfter: Long?
)