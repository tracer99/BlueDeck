package com.bluedeck.data.obd.transport

object ObdResponseParser {

    fun parseHexPayload(response: String): ByteArray? {
        val cleaned = response
            .replace("SEARCHING...", "")
            .replace("\r", " ")
            .replace("\n", " ")
            .uppercase()
        val tokens = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        val dataStart = tokens.indexOfFirst { it == "62" || it == "41" || it == "7F" }
        if (dataStart < 0) return null

        val mode = tokens[dataStart]
        val payloadStart = when (mode) {
            "62" -> dataStart + 4 // 62 PID_HI PID_LO data...
            "41" -> dataStart + 2 // 41 PID data...
            else -> return null
        }
        if (payloadStart >= tokens.size) return null

        return tokens.drop(payloadStart)
            .takeWhile { it.length == 2 && it.all { ch -> ch in '0'..'9' || ch in 'A'..'F' } }
            .mapNotNull { token -> token.toIntOrNull(16)?.toByte() }
            .toByteArray()
    }

    fun isPositiveResponse(response: String): Boolean {
        val upper = response.uppercase()
        return upper.contains(" 62 ") || upper.contains("62 ") || upper.contains(" 41 ") || upper.contains("41 ")
    }

    fun isNoData(response: String): Boolean =
        response.contains("NO DATA", ignoreCase = true) ||
            response.contains("UNABLE TO CONNECT", ignoreCase = true) ||
            response.contains("ERROR", ignoreCase = true) && !isPositiveResponse(response)
}
