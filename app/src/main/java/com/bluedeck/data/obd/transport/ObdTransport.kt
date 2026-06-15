package com.bluedeck.data.obd.transport

interface ObdTransport {
    suspend fun connect()
    suspend fun disconnect()
    val isConnected: Boolean
    suspend fun write(command: String)
    suspend fun readLine(timeoutMs: Long = 3000L): String?
}
