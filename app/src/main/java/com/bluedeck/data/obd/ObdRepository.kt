package com.bluedeck.data.obd

import android.content.Context
import com.bluedeck.data.obd.db.ObdCellSnapshotEntity
import com.bluedeck.data.obd.db.ObdDatabase
import com.bluedeck.data.obd.db.ObdSampleEntity
import com.bluedeck.data.obd.db.ObdSessionEntity
import com.bluedeck.data.obd.pid.HkmcEvPidEngine
import com.bluedeck.data.obd.transport.BluetoothClassicObdTransport
import com.bluedeck.data.obd.transport.Elm327Client
import com.bluedeck.data.obd.transport.ObdTransport
import com.bluedeck.data.obd.transport.WifiObdTransport
import com.bluedeck.data.repository.PreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObdRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ObdDatabase,
    private val preferencesManager: PreferencesManager,
    private val bluetoothTransport: BluetoothClassicObdTransport,
    private val wifiTransport: WifiObdTransport,
    private val elm327Client: Elm327Client,
    private val pidEngine: HkmcEvPidEngine,
    private val retentionManager: ObdLogRetentionManager,
    private val csvExporter: ObdCsvExporter
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pollMutex = Mutex()

    private val _connectionState = MutableStateFlow(ObdConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ObdConnectionState> = _connectionState.asStateFlow()

    private val _latestSnapshot = MutableStateFlow(ObdSnapshot())
    val latestSnapshot: StateFlow<ObdSnapshot> = _latestSnapshot.asStateFlow()

    private val _activeSessionId = MutableStateFlow<Long?>(null)
    val activeSessionId: StateFlow<Long?> = _activeSessionId.asStateFlow()

    val sessions = database.sessionDao().observeAll()

    private var loggingJob: kotlinx.coroutines.Job? = null
    private var consecutiveFailures = 0

    suspend fun loadAdapterConfig(): ObdAdapterConfig {
        val prefs = preferencesManager
        return ObdAdapterConfig(
            transportType = ObdTransportType.valueOf(prefs.obdTransportType.first()),
            bluetoothAddress = prefs.obdBluetoothAddress.first(),
            bluetoothName = prefs.obdBluetoothName.first(),
            wifiHost = prefs.obdWifiHost.first(),
            wifiPort = prefs.obdWifiPort.first(),
            profileId = HkmcEvProfileId.fromKey(prefs.obdProfileId.first()),
            sampleIntervalSeconds = prefs.obdSampleIntervalSeconds.first(),
            autoConnect = prefs.obdAutoConnect.first(),
            autoStartLogging = prefs.obdAutoStartLogging.first()
        )
    }

    suspend fun connect(config: ObdAdapterConfig): Result<Unit> = runCatching {
        _connectionState.value = ObdConnectionState.CONNECTING
        val transport = resolveTransport(config)
        when (config.transportType) {
            ObdTransportType.BLUETOOTH -> {
                val address = config.bluetoothAddress ?: error("Bluetooth address required")
                bluetoothTransport.connect(address)
            }
            ObdTransportType.WIFI -> {
                wifiTransport.configure(config.wifiHost, config.wifiPort)
                wifiTransport.connect()
            }
        }
        elm327Client.initialize(transport)
        _connectionState.value = ObdConnectionState.CONNECTED
        consecutiveFailures = 0
    }.onFailure {
        _connectionState.value = ObdConnectionState.ERROR
    }

    suspend fun disconnect() {
        loggingJob?.cancel()
        loggingJob = null
        bluetoothTransport.disconnect()
        wifiTransport.disconnect()
        _activeSessionId.value?.let { endSession(it) }
        _connectionState.value = ObdConnectionState.DISCONNECTED
    }

    suspend fun startLogging(vin: String?, config: ObdAdapterConfig) {
        if (_connectionState.value != ObdConnectionState.CONNECTED &&
            _connectionState.value != ObdConnectionState.LOGGING
        ) {
            connect(config).getOrThrow()
        }
        val sessionId = database.sessionDao().insert(
            ObdSessionEntity(
                vin = vin,
                adapterId = config.bluetoothAddress ?: "${config.wifiHost}:${config.wifiPort}",
                profileId = config.profileId.name,
                startedAt = System.currentTimeMillis()
            )
        )
        _activeSessionId.value = sessionId
        _connectionState.value = ObdConnectionState.LOGGING
        loggingJob?.cancel()
        loggingJob = scope.launch { pollLoop(sessionId, config) }
    }

    suspend fun stopLogging() {
        loggingJob?.cancel()
        loggingJob = null
        _activeSessionId.value?.let { endSession(it) }
        _connectionState.value = if (currentTransport()?.isConnected == true) {
            ObdConnectionState.CONNECTED
        } else {
            ObdConnectionState.DISCONNECTED
        }
    }

    suspend fun getSessionSummary(id: Long): ObdSessionSummary? {
        val session = database.sessionDao().getById(id) ?: return null
        return session.toSummary()
    }

    suspend fun getSamples(sessionId: Long) = database.sampleDao().getForSession(sessionId)

    suspend fun getLatestCellSnapshot(sessionId: Long): CellVoltageSnapshot? {
        val entity = database.cellSnapshotDao().getLatestForSession(sessionId) ?: return null
        val array = JSONArray(entity.cellVoltagesJson)
        val voltages = List(array.length()) { index -> array.getDouble(index) }
        return CellVoltageSnapshot(entity.timestamp, voltages)
    }

    suspend fun exportSessionCsv(sessionId: Long, cacheDir: java.io.File): Pair<java.io.File, java.io.File?> {
        val session = database.sessionDao().getById(sessionId) ?: error("Session not found")
        val samples = database.sampleDao().getForSession(sessionId)
        val mainFile = csvExporter.writeToCache(
            cacheDir,
            csvExporter.sessionFileName(session),
            csvExporter.buildSessionCsv(session, samples)
        )
        val cells = database.cellSnapshotDao().getAllForSession(sessionId)
        val cellsFile = if (cells.isNotEmpty()) {
            csvExporter.writeToCache(
                cacheDir,
                csvExporter.cellsFileName(session),
                csvExporter.buildCellsCsv(cells)
            )
        } else {
            null
        }
        return mainFile to cellsFile
    }

    suspend fun pruneLogs() {
        retentionManager.pruneExpiredLogs(_activeSessionId.value)
    }

    suspend fun clearAllLogs() {
        retentionManager.clearAllLogs(_activeSessionId.value)
    }

    suspend fun getStorageUsage() = retentionManager.getStorageUsage()

    private suspend fun pollLoop(sessionId: Long, config: ObdAdapterConfig) {
        var lastCellPoll = 0L
        val intervalMs = config.sampleIntervalSeconds.coerceIn(5, 60) * 1000L
        while (true) {
            pollMutex.withLock {
                val transport = currentTransport() ?: return@withLock
                if (!transport.isConnected) {
                    consecutiveFailures++
                    if (consecutiveFailures >= 5) {
                        endSession(sessionId)
                        _connectionState.value = ObdConnectionState.DISCONNECTED
                        return
                    }
                } else {
                    val snapshot = pidEngine.readFastSnapshot(transport, config.profileId)
                    _latestSnapshot.value = snapshot
                    appendSample(sessionId, snapshot)
                    consecutiveFailures = 0

                    val now = System.currentTimeMillis()
                    if (now - lastCellPoll >= 60_000L) {
                        lastCellPoll = now
                        val cells = pidEngine.readCellVoltages(transport, config.profileId)
                        if (cells.isNotEmpty()) {
                            database.cellSnapshotDao().insert(
                                ObdCellSnapshotEntity(
                                    sessionId = sessionId,
                                    timestamp = now,
                                    cellVoltagesJson = JSONArray(cells).toString()
                                )
                            )
                        }
                    }
                }
            }
            delay(intervalMs)
        }
    }

    private suspend fun appendSample(sessionId: Long, snapshot: ObdSnapshot) {
        database.sampleDao().insert(
            ObdSampleEntity(
                sessionId = sessionId,
                timestamp = snapshot.capturedAt,
                auxVoltageV = snapshot.auxVoltageV,
                aux12vState = snapshot.aux12vState.name,
                tractionSoc = snapshot.tractionSocPercent,
                tractionSoh = snapshot.tractionSohPercent,
                isCharging = snapshot.isCharging,
                batteryTempMinC = snapshot.batteryTempMinC,
                batteryTempMaxC = snapshot.batteryTempMaxC,
                batteryTempAvgC = snapshot.batteryTempAvgC,
                batteryHeaterState = snapshot.batteryHeaterState.name,
                batteryFanMode = snapshot.batteryFanMode,
                cellVoltageMinV = snapshot.cellVoltageMinV,
                cellVoltageMaxV = snapshot.cellVoltageMaxV,
                cellVoltageAvgV = snapshot.cellVoltageAvgV,
                cellVoltageDeviationV = snapshot.cellVoltageDeviationV,
                frontMotorRpm = snapshot.frontMotorRpm,
                rearMotorRpm = snapshot.rearMotorRpm,
                brakeLightOn = snapshot.brakeLightOn,
                headlightOn = snapshot.headlightOn
            )
        )
        val session = database.sessionDao().getById(sessionId) ?: return
        database.sessionDao().update(
            session.copy(
                sampleCount = session.sampleCount + 1,
                lightingSupported = snapshot.lightingSupported
            )
        )
    }

    private suspend fun endSession(sessionId: Long) {
        val session = database.sessionDao().getById(sessionId) ?: return
        database.sessionDao().update(
            session.copy(endedAt = System.currentTimeMillis())
        )
        retentionManager.updateSessionSize(sessionId)
        retentionManager.pruneExpiredLogs(null)
        _activeSessionId.value = null
        ObdDriveSyncWorker.enqueue(context, sessionId)
    }

    private fun currentTransport(): ObdTransport? = when {
        bluetoothTransport.isConnected -> bluetoothTransport
        wifiTransport.isConnected -> wifiTransport
        else -> null
    }

    private fun resolveTransport(config: ObdAdapterConfig): ObdTransport = when (config.transportType) {
        ObdTransportType.BLUETOOTH -> bluetoothTransport
        ObdTransportType.WIFI -> wifiTransport
    }

    private fun ObdSessionEntity.toSummary() = ObdSessionSummary(
        id = id,
        vin = vin,
        profileId = profileId,
        startedAt = startedAt,
        endedAt = endedAt,
        sampleCount = sampleCount,
        sizeBytes = sizeBytes,
        driveFileId = driveFileId,
        lightingSupported = lightingSupported
    )
}
