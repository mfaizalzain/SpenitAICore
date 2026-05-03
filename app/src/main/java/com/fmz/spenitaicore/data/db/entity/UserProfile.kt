package com.fmz.spenitaicore.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "UserProfiles")
data class UserProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "google_id") val googleId: String = "",
    @ColumnInfo(name = "apple_id") val appleId: String? = null,
    @ColumnInfo(name = "name") val name: String = "",
    @ColumnInfo(name = "email") val email: String = "",
    @ColumnInfo(name = "photo_url") val photoUrl: String? = null,
    @ColumnInfo(name = "id_token") val idToken: String? = null,
    @ColumnInfo(name = "access_token") val accessToken: String? = null,
    @ColumnInfo(name = "google_refresh_token") val googleRefreshToken: String? = null,
    @ColumnInfo(name = "access_token_expires_at") val accessTokenExpiresAt: Long? = null,
    @ColumnInfo(name = "has_drive_access") val hasDriveAccess: Boolean = false,
    @ColumnInfo(name = "is_passkey_enabled") val isPasskeyEnabled: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
