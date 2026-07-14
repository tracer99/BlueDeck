package com.bluedeck.data.obd

import com.bluedeck.data.obd.db.ObdSampleEntity
import com.bluedeck.data.obd.db.ObdSessionEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdCsvExporterTest {

    private val exporter = ObdCsvExporter()

    @Test
    fun buildSessionCsv_includesHeaderAndSampleRow() {
        val session = ObdSessionEntity(
            id = 1,
            profileId = "KONA_NIRO_64",
            startedAt = 1_700_000_000_000
        )
        val samples = listOf(
            ObdSampleEntity(
                sessionId = 1,
                timestamp = 1_700_000_010_000,
                tractionSoc = 72.0,
                tractionSoh = 98.5,
                frontMotorRpm = 1200
            )
        )
        val csv = exporter.buildSessionCsv(session, samples)
        assertTrue(csv.contains("traction_soc"))
        assertTrue(csv.contains("72.0"))
        assertTrue(csv.contains("98.5"))
        assertTrue(csv.contains("1200"))
    }
}
