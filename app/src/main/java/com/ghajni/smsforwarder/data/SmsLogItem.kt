package com.ghajni.smsforwarder.data

data class SmsLogItem(
    val id: Long = 0,
    val sender: String,
    val body: String,
    val forwardedTo: String,
    val timestamp: Long,
    val status: String // "SENT", "FAILED", "FILTERED", "DISABLED"
)

