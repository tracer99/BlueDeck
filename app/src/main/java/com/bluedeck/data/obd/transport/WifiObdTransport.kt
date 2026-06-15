package com.bluedeck.data.obd.transport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiObdTransport @Inject constructor() : ObdTransport {

    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: OutputStreamWriter? = null
    private var host: String = "192.168.0.10"
    private var port: Int = 35000

    override val isConnected: Boolean
        get() = socket?.isConnected == true && socket?.isClosed == false

    fun configure(host: String, port: Int) {
        this.host = host
        this.port = port
    }

    override suspend fun connect() = withContext(Dispatchers.IO) {
        disconnect()
        val s = Socket()
        s.connect(InetSocketAddress(host, port), 10_000)
        socket = s
        reader = BufferedReader(InputStreamReader(s.getInputStream()))
        writer = OutputStreamWriter(s.getOutputStream())
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { reader?.close() }
        runCatching { writer?.close() }
        runCatching { socket?.close() }
        reader = null
        writer = null
        socket = null
    }

    override suspend fun write(command: String) = withContext(Dispatchers.IO) {
        val w = writer ?: throw IllegalStateException("Not connected")
        w.write(command)
        w.write("\r")
        w.flush()
    }

    override suspend fun readLine(timeoutMs: Long): String? = withContext(Dispatchers.IO) {
        val r = reader ?: return@withContext null
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (r.ready()) {
                return@withContext r.readLine()?.trim()
            }
            Thread.sleep(25)
        }
        null
    }
}
