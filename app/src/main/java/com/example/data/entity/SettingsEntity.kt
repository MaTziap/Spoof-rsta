package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val listenHost: String = "127.0.0.1",
    val listenPort: Int = 40443,
    val connectIp: String = "104.18.38.202",
    val connectPort: Int = 443,
    val fakeSni: String = "cdnjs.cloudflare.com",
    val bypassMethod: String = "combined", // "fragment", "fake_sni", "combined"
    val fragmentStrategy: String = "sni_split", // "sni_split", "half", "multi", "tls_record_frag"
    val fragmentDelay: Double = 0.1,
    val useTtlTrick: Boolean = true
)
