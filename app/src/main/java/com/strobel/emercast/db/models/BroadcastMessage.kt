package com.strobel.emercast.db.models

data class BroadcastMessage (
    val id: String,
    val created: Long,
    val systemMessage: Boolean,
    val forwardUntil: Long,
    val latitude: Float,
    val longitude: Float,
    val radius: Int,
    val category: String,
    val severity: Int,
    val title: String,
    val content: String,

    val issuedAuthorityId: String,
    val issuerSignature: String,

    val received: Long,
    val directlyReceived: Boolean,
    var systemMessageRegardingAuthority: String?,

    var forwardUntilOverride: Long?
)