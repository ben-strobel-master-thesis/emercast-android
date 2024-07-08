package com.strobel.emercast.db.models

data class BroadcastMessage (
    val id: String,
    val created: Long,
    val modified: Long,
    val received: Long,
    val directlyReceived: Int,
    val forwardUntil: Long,
    val latitude: Float,
    val longitude: Float,
    val radius: Int,
    val category: String,
    val severity: Int,
    val title: String,
    val content: String
)