package com.bluedeck.data.obd.pid

import com.bluedeck.data.obd.Aux12vState
import com.bluedeck.data.obd.BatteryHeaterState
import com.bluedeck.data.obd.HkmcEvProfileId
import com.bluedeck.data.obd.ObdSnapshot
import com.bluedeck.data.obd.transport.ObdResponseParser
import com.bluedeck.data.obd.transport.ObdTransport
import com.bluedeck.data.obd.transport.Elm327Client
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class HkmcEvPidEngine @Inject constructor(
    private val elm327Client: Elm327Client
) {
    private var lastAuxVoltage: Double? = null
    private var lightingProbeFailed = false

    suspend fun readFastSnapshot(transport: ObdTransport, profile: HkmcEvProfileId): ObdSnapshot {
        val bms101 = elm327Client.queryPid(transport, "7E4", "220101")
        val bms105 = elm327Client.queryPid(transport, "7E4", "220105")
        val std42 = elm327Client.queryStandardPid(transport, "42")

        val payload101 = ObdResponseParser.parseHexPayload(bms101)
        val payload105 = ObdResponseParser.parseHexPayload(bms105)
        val payload42 = ObdResponseParser.parseHexPayload(std42)

        val lighting = if (!lightingProbeFailed) readLighting(transport) else ObdSnapshot()

        val auxFromBms = payload101?.getOrNull(29)?.let { (it.toInt() and 0xFF) * 0.1 }
        val auxFromStd = payload42?.getOrNull(0)?.let { (it.toInt() and 0xFF) / 4.0 }
        val auxVoltage = auxFromBms ?: auxFromStd

        val motor1 = signedInt16(payload101, 53, 54)
        val motor2 = signedInt16(payload101, 55, 56)
        val (frontRpm, rearRpm) = when (profile) {
            HkmcEvProfileId.IONIQ_5_6 -> motor2 to motor1
            else -> motor1 to motor2
        }

        val heaterTemp1 = payload105?.getOrNull(23)?.toInt()?.let { signedByte(it) }?.toDouble()
        val fanMode = payload101?.getOrNull(27)?.toInt()?.and(0xFF)
        val heaterState = deriveHeaterState(heaterTemp1, fanMode)

        val minCell = payload101?.getOrNull(23)?.let { (it.toInt() and 0xFF) / 50.0 }
        val maxCell = payload101?.getOrNull(23)?.let { _ ->
            payload101.getOrNull(23)?.let { (it.toInt() and 0xFF) / 50.0 }
        }
        // min cell z (index 25), max cell x (index 23) per Kona CSV
        val minCellV = payload101?.getOrNull(25)?.let { (it.toInt() and 0xFF) / 50.0 }
        val maxCellV = payload101?.getOrNull(23)?.let { (it.toInt() and 0xFF) / 50.0 }
        val avgCellV = if (minCellV != null && maxCellV != null) (minCellV + maxCellV) / 2.0 else null
        val cellDev = payload105?.getOrNull(20)?.let { (it.toInt() and 0xFF) / 50.0 }

        val minTemp = payload101?.getOrNull(15)?.let { signedByte(it.toInt()).toDouble() }
        val maxTemp = payload101?.getOrNull(14)?.let { signedByte(it.toInt()).toDouble() }
        val modTemps = listOfNotNull(
            payload101?.getOrNull(16)?.let { signedByte(it.toInt()).toDouble() },
            payload101?.getOrNull(17)?.let { signedByte(it.toInt()).toDouble() },
            payload101?.getOrNull(18)?.let { signedByte(it.toInt()).toDouble() },
            payload101?.getOrNull(19)?.let { signedByte(it.toInt()).toDouble() }
        )
        val avgTemp = when {
            modTemps.isNotEmpty() -> modTemps.average()
            minTemp != null && maxTemp != null -> (minTemp + maxTemp) / 2.0
            else -> null
        }

        val relayByte = payload101?.getOrNull(9)?.toInt()?.and(0xFF) ?: 0
        val isCharging = (relayByte shr 7) and 1 == 1

        val auxState = deriveAuxState(auxVoltage)

        return ObdSnapshot(
            auxVoltageV = auxVoltage,
            aux12vState = auxState,
            tractionSocPercent = payload101?.getOrNull(4)?.let { (it.toInt() and 0xFF) / 2.0 },
            tractionSohPercent = payload105?.let { p ->
                if (p.size >= 27) {
                    val z = p[25].toInt() and 0xFF
                    val aa = p[26].toInt() and 0xFF
                    (z * 256 + aa) / 10.0
                } else null
            },
            isCharging = isCharging,
            batteryTempMinC = minTemp,
            batteryTempMaxC = maxTemp,
            batteryTempAvgC = avgTemp,
            batteryHeaterState = heaterState,
            batteryFanMode = fanMode,
            cellVoltageMinV = minCellV,
            cellVoltageMaxV = maxCellV,
            cellVoltageAvgV = avgCellV,
            cellVoltageDeviationV = cellDev,
            frontMotorRpm = frontRpm,
            rearMotorRpm = rearRpm,
            brakeLightOn = lighting.brakeLightOn,
            headlightOn = lighting.headlightOn,
            lightingSupported = lighting.lightingSupported
        )
    }

    suspend fun readCellVoltages(transport: ObdTransport, profile: HkmcEvProfileId): List<Double> {
        val subPids = cellSubPidsFor(profile)
        val voltages = mutableListOf<Double>()
        for (subPid in subPids) {
            val response = elm327Client.queryPid(transport, "7E4", subPid)
            val payload = ObdResponseParser.parseHexPayload(response) ?: continue
            payload.drop(4).forEach { byte ->
                voltages += (byte.toInt() and 0xFF) / 50.0
            }
            if (voltages.size >= profile.cellCount) break
        }
        return voltages.take(profile.cellCount)
    }

    private suspend fun readLighting(transport: ObdTransport): ObdSnapshot {
        return runCatching {
            // Best-effort cluster/BCM probe on header 7B3 (Ioniq 5 cluster data)
            val response = elm327Client.queryPid(transport, "7B3", "220100")
            if (ObdResponseParser.isNoData(response)) {
                lightingProbeFailed = true
                return ObdSnapshot(lightingSupported = false)
            }
            val payload = ObdResponseParser.parseHexPayload(response) ?: run {
                lightingProbeFailed = true
                return ObdSnapshot(lightingSupported = false)
            }
            val statusByte = payload.getOrNull(0)?.toInt()?.and(0xFF) ?: return ObdSnapshot(lightingSupported = false)
            ObdSnapshot(
                headlightOn = (statusByte and 0x01) != 0,
                brakeLightOn = (statusByte and 0x02) != 0,
                lightingSupported = true
            )
        }.getOrElse {
            lightingProbeFailed = true
            ObdSnapshot(lightingSupported = false)
        }
    }

    private fun cellSubPidsFor(profile: HkmcEvProfileId): List<String> = when (profile) {
        HkmcEvProfileId.IONIQ_5_6 -> listOf("220102", "220103", "220104", "22010A", "22010B", "22010C")
        else -> listOf("220102", "220103")
    }

    private fun deriveHeaterState(heaterTemp: Double?, fanMode: Int?): BatteryHeaterState {
        if (heaterTemp != null && heaterTemp > 5.0) return BatteryHeaterState.HEATING
        if (fanMode != null && fanMode > 0) return BatteryHeaterState.HEATING
        if (heaterTemp != null || fanMode != null) return BatteryHeaterState.OFF
        return BatteryHeaterState.UNKNOWN
    }

    private fun deriveAuxState(voltage: Double?): Aux12vState {
        if (voltage == null) return Aux12vState.UNKNOWN
        val prev = lastAuxVoltage
        lastAuxVoltage = voltage
        if (prev == null) return Aux12vState.STABLE
        val delta = voltage - prev
        return when {
            delta > 0.05 -> Aux12vState.CHARGING
            delta < -0.05 -> Aux12vState.DISCHARGING
            else -> Aux12vState.STABLE
        }
    }

    private fun signedInt16(payload: ByteArray?, highIndex: Int, lowIndex: Int): Int? {
        if (payload == null || highIndex >= payload.size || lowIndex >= payload.size) return null
        val value = ((payload[highIndex].toInt() and 0xFF) shl 8) or (payload[lowIndex].toInt() and 0xFF)
        return if (value and 0x8000 != 0) value - 0x10000 else value
    }

    private fun signedByte(value: Int): Int {
        val v = value and 0xFF
        return if (v > 127) v - 256 else v
    }
}
