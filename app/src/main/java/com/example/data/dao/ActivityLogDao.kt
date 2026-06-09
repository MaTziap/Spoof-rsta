package com.example.data.dao

import androidx.room.*
import com.example.data.entity.ActivityLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC LIMIT 200")
    fun getAllLogsFlow(): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_logs WHERE connId = :connId LIMIT 1")
    suspend fun getLogByConnId(connId: String): ActivityLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ActivityLogEntity): Long

    @Update
    suspend fun updateLog(log: ActivityLogEntity)

    @Query("DELETE FROM activity_logs")
    suspend fun clearAllLogs()
}
