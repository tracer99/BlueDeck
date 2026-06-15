package com.bluedeck.data.obd.transport

import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Elm327Client @Inject constructor() {

    suspend fun initialize(transport: ObdTransport) {
        sendCommand(transport, "ATZ", delayAfterMs = 1000)
        sendCommand(transport, "ATE0")
        sendCommand(transport, "ATL0")
        sendCommand(transport, "ATH1")
        sendCommand(transport, "ATSP0")
        sendCommand(transport, "ATST64")
    }

    suspend fun sendCommand(transport: ObdTransport, command: String, delayAfterMs: Long = 150): String {
        transport.write(command)
        delay(delayAfterMs)
        val lines = mutableListOf<String>()
        repeat(8) {
            val line = transport.readLine(timeoutMs = 2000) ?: return@repeat
            if (line.isNotBlank() && line != ">") {
                lines += line
            }
            if (line.contains(">")) return@repeat
        }
        return lines.joinToString(" ")
    }

    suspend fun queryPid(transport: ObdTransport, header: String, modeAndPid: String): String {
        sendCommand(transport, "ATSH$header")
        return sendCommand(transport, modeAndPid, delayAfterMs = 300)
    }

    suspend fun queryStandardPid(transport: ObdTransport, pid: String): String {
        sendCommand(transport, "ATSH7E0")
        return sendCommand(transport, "01$pid", delayAfterMs = 300)
    }
}
