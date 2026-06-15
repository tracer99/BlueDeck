package com.bluedeck.data.obd

import com.bluedeck.data.obd.db.ObdCellSnapshotEntity
import com.bluedeck.data.obd.db.ObdSampleEntity
import com.bluedeck.data.obd.db.ObdSessionEntity
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObdCsvExporter @Inject constructor() {

    fun buildSessionCsv(
        session: ObdSessionEntity,
        samples: List<ObdSampleEntity>
    ): String {
        val header = listOf(
            "timestamp",
            "aux_voltage_v", "aux_12v_state", "traction_soc", "traction_soh", "is_charging",
            "battery_temp_min_c", "battery_temp_max_c", "battery_temp_avg_c",
            "battery_heater_state", "battery_fan_mode",
            "cell_voltage_min_v", "cell_voltage_max_v", "cell_voltage_avg_v", "cell_voltage_deviation_v",
            "front_motor_rpm", "rear_motor_rpm",
            "brake_light_on", "headlight_on"
        ).joinToString(",")
        val rows = samples.map { sample ->
            listOf(
                sample.timestamp.toString(),
                sample.auxVoltageV?.toString().orEmpty(),
                sample.aux12vState.orEmpty(),
                sample.tractionSoc?.toString().orEmpty(),
                sample.tractionSoh?.toString().orEmpty(),
                sample.isCharging?.toString().orEmpty(),
                sample.batteryTempMinC?.toString().orEmpty(),
                sample.batteryTempMaxC?.toString().orEmpty(),
                sample.batteryTempAvgC?.toString().orEmpty(),
                sample.batteryHeaterState.orEmpty(),
                sample.batteryFanMode?.toString().orEmpty(),
                sample.cellVoltageMinV?.toString().orEmpty(),
                sample.cellVoltageMaxV?.toString().orEmpty(),
                sample.cellVoltageAvgV?.toString().orEmpty(),
                sample.cellVoltageDeviationV?.toString().orEmpty(),
                sample.frontMotorRpm?.toString().orEmpty(),
                sample.rearMotorRpm?.toString().orEmpty(),
                sample.brakeLightOn?.toString().orEmpty(),
                sample.headlightOn?.toString().orEmpty()
            ).joinToString(",")
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    fun buildCellsCsv(snapshots: List<ObdCellSnapshotEntity>): String {
        val header = "timestamp,cell_index,voltage_v"
        val rows = mutableListOf<String>()
        snapshots.forEach { snapshot ->
            val array = JSONArray(snapshot.cellVoltagesJson)
            for (i in 0 until array.length()) {
                rows += "${snapshot.timestamp},$i,${array.getDouble(i)}"
            }
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    fun sessionFileName(session: ObdSessionEntity): String {
        val date = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(session.startedAt))
        val vin = session.vin?.takeLast(6) ?: "vehicle"
        return "bluedeck-obd-$vin-$date.csv"
    }

    fun cellsFileName(session: ObdSessionEntity): String =
        sessionFileName(session).replace(".csv", "-cells.csv")

    fun writeToCache(cacheDir: File, fileName: String, content: String): File {
        val file = File(cacheDir, fileName)
        file.writeText(content)
        return file
    }
}
