package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val connId: String,
    val clientAddr: String,
    val serverAddr: String,
    val realSni: String = "",
    val fakeSni: String = "",
    val method: String = "",
    val bytesC2S: Long = 0,
    val bytesS2C: Long = 0,
    val duration: Double = 0.0,
    val status: String = "connecting", // "connecting", "active", "blocked", "closed", "error"
    val serverReplied: Boolean = false,
    val errorMessage: String? = null
)
