package com.example.data.repository

import com.example.data.dao.ActivityLogDao
import com.example.data.dao.SettingsDao
import com.example.data.entity.ActivityLogEntity
import com.example.data.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow

class ProxyRepository(
    private val settingsDao: SettingsDao,
    private val activityLogDao: ActivityLogDao
) {
    val settingsFlow: Flow<SettingsEntity?> = settingsDao.getSettingsFlow()
    val logsFlow: Flow<List<ActivityLogEntity>> = activityLogDao.getAllLogsFlow()

    suspend fun getSettings(): SettingsEntity? = settingsDao.getSettings()

    suspend fun saveSettings(settings: SettingsEntity) {
        settingsDao.saveSettings(settings)
    }

    suspend fun getLogByConnId(connId: String): ActivityLogEntity? {
        return activityLogDao.getLogByConnId(connId)
    }

    suspend fun insertLog(log: ActivityLogEntity): Long {
        return activityLogDao.insertLog(log)
    }

    suspend fun updateLog(log: ActivityLogEntity) {
        activityLogDao.updateLog(log)
    }

    suspend fun clearLogs() {
        activityLogDao.clearAllLogs()
    }
}
