package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.entity.ActivityLogEntity
import com.example.data.entity.SettingsEntity
import com.example.data.repository.ProxyRepository
import com.example.proxy.DpiBypassProxyServer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ProxyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ProxyRepository

    // Settings Flow (Emits the loaded user settings or default if not set)
    val settingsState: StateFlow<SettingsEntity>
    
    // Historic and Active connection log flow from Room DB
    val logsState: StateFlow<List<ActivityLogEntity>>

    // Live Server service engine states
    val isRunning = DpiBypassProxyServer.serviceState
    val liveTotalConnections = DpiBypassProxyServer.totalConns
    val liveC2SBytes = DpiBypassProxyServer.totalC2S
    val liveS2CBytes = DpiBypassProxyServer.totalS2C

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ProxyRepository(database.settingsDao(), database.activityLogDao())

        // Reactively fetch settings row
        settingsState = repository.settingsFlow
            .map { it ?: SettingsEntity() } // Provide defaults if null
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = SettingsEntity()
            )

        // Reactively fetch logs flow
        logsState = repository.logsFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        // Preseed defaults in Room if empty on launch
        viewModelScope.launch {
            val current = repository.getSettings()
            if (current == null) {
                repository.saveSettings(SettingsEntity())
            }
        }
    }

    fun saveSettings(
        listenHost: String,
        listenPort: Int,
        connectIp: String,
        connectPort: Int,
        fakeSni: String,
        bypassMethod: String,
        fragmentStrategy: String,
        fragmentDelay: Double,
        useTtlTrick: Boolean
    ) {
        viewModelScope.launch {
            val updated = SettingsEntity(
                id = 1,
                listenHost = listenHost,
                listenPort = listenPort,
                connectIp = connectIp,
                connectPort = connectPort,
                fakeSni = fakeSni,
                bypassMethod = bypassMethod,
                fragmentStrategy = fragmentStrategy,
                fragmentDelay = fragmentDelay,
                useTtlTrick = useTtlTrick
            )
            repository.saveSettings(updated)
            
            // If proxy is actively running, restart to apply new configuration values
            if (isRunning.value) {
                DpiBypassProxyServer.stop()
                DpiBypassProxyServer.start(repository, updated)
            }
        }
    }

    fun toggleProxyService() {
        val currentSettings = settingsState.value
        if (isRunning.value) {
            DpiBypassProxyServer.stop()
        } else {
            DpiBypassProxyServer.start(repository, currentSettings)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }
}
