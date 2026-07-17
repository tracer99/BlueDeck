package com.bluedeck.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluedeck.data.obd.HkmcEvProfileId
import com.bluedeck.data.obd.ObdAdapterConfig
import com.bluedeck.data.obd.ObdConnectionState
import com.bluedeck.data.obd.ObdDriveSyncManager
import com.bluedeck.data.obd.ObdLoggingService
import com.bluedeck.data.obd.ObdRepository
import com.bluedeck.data.obd.ObdStorageUsage
import com.bluedeck.data.obd.ObdTransportType
import com.bluedeck.data.obd.db.ObdSessionEntity
import com.bluedeck.data.repository.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ObdViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val obdRepository: ObdRepository,
    private val preferencesManager: PreferencesManager,
    private val driveSyncManager: ObdDriveSyncManager
) : ViewModel() {

    val connectionState = obdRepository.connectionState
        .stateIn(viewModelScope, SharingStarted.Eagerly, ObdConnectionState.DISCONNECTED)

    val latestSnapshot = obdRepository.latestSnapshot
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.bluedeck.data.obd.ObdSnapshot())

    val sessions = obdRepository.sessions
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _adapterConfig = MutableStateFlow(ObdAdapterConfig())
    val adapterConfig: StateFlow<ObdAdapterConfig> = _adapterConfig.asStateFlow()

    val retentionDays = preferencesManager.obdLogRetentionDays
        .stateIn(viewModelScope, SharingStarted.Eagerly, 30)

    val maxStorageMb = preferencesManager.obdLogMaxStorageMb
        .stateIn(viewModelScope, SharingStarted.Eagerly, 100)

    val driveSyncEnabled = preferencesManager.obdDriveSyncEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val driveLastSyncAt = preferencesManager.obdDriveLastSyncAt
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    private val _storageUsage = kotlinx.coroutines.flow.MutableStateFlow(ObdStorageUsage(0, 0))
    val storageUsage: StateFlow<ObdStorageUsage> = _storageUsage

    private val _statusMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    init {
        refreshStorageUsage()
        reloadAdapterConfig()
    }

    private fun reloadAdapterConfig() = viewModelScope.launch {
        _adapterConfig.value = obdRepository.loadAdapterConfig()
    }

    fun refreshStorageUsage() = viewModelScope.launch {
        _storageUsage.value = obdRepository.getStorageUsage()
    }

    fun connect() = viewModelScope.launch {
        val config = adapterConfig.value
        obdRepository.connect(config)
            .onSuccess { _statusMessage.value = "Connected to OBD adapter" }
            .onFailure { _statusMessage.value = it.message ?: "Connection failed" }
    }

    fun disconnect() = viewModelScope.launch {
        obdRepository.disconnect()
        _statusMessage.value = "Disconnected"
    }

    fun startLogging(vin: String?) = viewModelScope.launch {
        ObdLoggingService.start(context)
        _statusMessage.value = "OBD logging started"
    }

    fun stopLogging() = viewModelScope.launch {
        ObdLoggingService.stop(context)
        _statusMessage.value = "OBD logging stopped"
    }

    fun exportSession(sessionId: Long, onFilesReady: (java.io.File, java.io.File?) -> Unit) = viewModelScope.launch {
        val files = obdRepository.exportSessionCsv(sessionId, context.cacheDir)
        onFilesReady(files.first, files.second)
    }

    fun syncSessionToDrive(sessionId: Long) = viewModelScope.launch {
        driveSyncManager.syncSession(sessionId)
            .onSuccess { _statusMessage.value = "Uploaded to Google Drive" }
            .onFailure { _statusMessage.value = it.message ?: "Drive sync failed" }
    }

    fun signInToDrive(launcher: (android.content.Intent) -> Unit) {
        if (!driveSyncManager.isConfigured()) {
            _statusMessage.value = "Configure google_drive_web_client_id in strings.xml"
            return
        }
        launcher(driveSyncManager.buildSignInClient().signInIntent)
    }

    fun setTransportType(type: ObdTransportType) = viewModelScope.launch {
        preferencesManager.setObdTransportType(type)
        reloadAdapterConfig()
    }

    fun setBluetoothDevice(name: String, address: String) = viewModelScope.launch {
        preferencesManager.setObdBluetoothDevice(name, address)
        reloadAdapterConfig()
    }

    fun setWifiEndpoint(host: String, port: Int) = viewModelScope.launch {
        preferencesManager.setObdWifiEndpoint(host, port)
        reloadAdapterConfig()
    }

    fun setProfile(profile: HkmcEvProfileId) = viewModelScope.launch {
        preferencesManager.setObdProfileId(profile.name)
        reloadAdapterConfig()
    }

    fun setSampleInterval(seconds: Int) = viewModelScope.launch {
        preferencesManager.setObdSampleIntervalSeconds(seconds)
        reloadAdapterConfig()
    }

    fun setAutoConnect(enabled: Boolean) = viewModelScope.launch {
        preferencesManager.setObdAutoConnect(enabled)
        reloadAdapterConfig()
    }

    fun setAutoStartLogging(enabled: Boolean) = viewModelScope.launch {
        preferencesManager.setObdAutoStartLogging(enabled)
        reloadAdapterConfig()
    }

    fun setRetentionDays(days: Int) = viewModelScope.launch {
        preferencesManager.setObdLogRetentionDays(days)
        obdRepository.pruneLogs()
        refreshStorageUsage()
    }

    fun setMaxStorageMb(mb: Int) = viewModelScope.launch {
        preferencesManager.setObdLogMaxStorageMb(mb)
        obdRepository.pruneLogs()
        refreshStorageUsage()
    }

    fun setDriveSyncEnabled(enabled: Boolean) = viewModelScope.launch {
        preferencesManager.setObdDriveSyncEnabled(enabled)
    }

    fun clearAllLogs() = viewModelScope.launch {
        obdRepository.clearAllLogs()
        refreshStorageUsage()
        _statusMessage.value = "OBD logs cleared"
    }

    fun reportStatus(message: String) {
        _statusMessage.value = message
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun sessionLabel(session: ObdSessionEntity): String {
        val start = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(session.startedAt))
        return "$start · ${session.sampleCount} samples"
    }
}
