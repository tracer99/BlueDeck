package com.bluedeck.data.obd

import com.bluedeck.data.obd.transport.ObdResponseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdResponseParserTest {

    @Test
    fun parseHexPayload_mode22_returnsDataBytes() {
        val response = "7EC 06 62 01 05 64 00"
        val payload = ObdResponseParser.parseHexPayload(response)
        assertNotNull(payload)
        assertTrue(payload!!.isNotEmpty())
    }

    @Test
    fun parseHexPayload_mode01_pid42() {
        val response = "7E8 04 41 42 8F AA"
        val payload = ObdResponseParser.parseHexPayload(response)
        assertNotNull(payload)
        assertEquals(0x8F, payload!![0].toInt() and 0xFF)
    }

    @Test
    fun isPositiveResponse_detects62() {
        assertTrue(ObdResponseParser.isPositiveResponse("7EC 10 14 62 01 01"))
    }
}
