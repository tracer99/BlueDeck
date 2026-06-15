package com.bluedeck.data.obd

enum class ObdTransportType { BLUETOOTH, WIFI }

enum class HkmcEvProfileId(val displayName: String, val cellCount: Int) {
    KONA_NIRO_64("Kona / Niro 64 kWh", 96),
    IONIQ_5_6("Ioniq 5 / 6", 180),
    IONIQ_EV_28("Ioniq Electric 28 kWh", 96),
    SOUL_EV_27("Soul EV 27 kWh", 96),
    GENERIC("Generic HKMC EV", 96);

    companion object {
        fun fromKey(key: String?): HkmcEvProfileId =
            entries.firstOrNull { it.name == key } ?: KONA_NIRO_64
    }
}

enum class BatteryHeaterState { OFF, HEATING, UNKNOWN }

enum class Aux12vState { CHARGING, DISCHARGING, STABLE, UNKNOWN }

enum class ObdConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    LOGGING,
    ERROR
}

data class ObdAdapterConfig(
    val transportType: ObdTransportType = ObdTransportType.BLUETOOTH,
    val bluetoothAddress: String? = null,
    val bluetoothName: String? = null,
    val wifiHost: String = "192.168.0.10",
    val wifiPort: Int = 35000,
    val profileId: HkmcEvProfileId = HkmcEvProfileId.KONA_NIRO_64,
    val sampleIntervalSeconds: Int = 10,
    val autoConnect: Boolean = false,
    val autoStartLogging: Boolean = false
)

data class ObdSnapshot(
    val auxVoltageV: Double? = null,
    val aux12vState: Aux12vState = Aux12vState.UNKNOWN,
    val tractionSocPercent: Double? = null,
    val tractionSohPercent: Double? = null,
    val isCharging: Boolean? = null,
    val batteryTempMinC: Double? = null,
    val batteryTempMaxC: Double? = null,
    val batteryTempAvgC: Double? = null,
    val batteryHeaterState: BatteryHeaterState = BatteryHeaterState.UNKNOWN,
    val batteryFanMode: Int? = null,
    val cellVoltageMinV: Double? = null,
    val cellVoltageMaxV: Double? = null,
    val cellVoltageAvgV: Double? = null,
    val cellVoltageDeviationV: Double? = null,
    val frontMotorRpm: Int? = null,
    val rearMotorRpm: Int? = null,
    val brakeLightOn: Boolean? = null,
    val headlightOn: Boolean? = null,
    val lightingSupported: Boolean = true,
    val capturedAt: Long = System.currentTimeMillis()
)

data class ObdSessionSummary(
    val id: Long,
    val vin: String?,
    val profileId: String,
    val startedAt: Long,
    val endedAt: Long?,
    val sampleCount: Int,
    val sizeBytes: Long,
    val driveFileId: String?,
    val lightingSupported: Boolean
)

data class ObdStorageUsage(
    val sessionCount: Int,
    val totalBytes: Long
)

data class CellVoltageSnapshot(
    val timestamp: Long,
    val voltages: List<Double>
)
