package com.fmz.spenitaicore.data.db.dao

import androidx.room.*
import com.fmz.spenitaicore.data.db.entity.UserProfile

@Dao
interface UserProfileDao {

    @Query("SELECT * FROM UserProfiles LIMIT 1")
    suspend fun getUserProfile(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: UserProfile): Long

    @Update
    suspend fun update(profile: UserProfile)

    @Query("DELETE FROM UserProfiles")
    suspend fun deleteAll()
}
