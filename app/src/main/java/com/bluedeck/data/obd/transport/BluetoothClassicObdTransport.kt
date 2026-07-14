package com.bluedeck.data.obd.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import javax.inject.Inject

class BluetoothClassicObdTransport @Inject constructor(
    @ApplicationContext private val context: Context
) : ObdTransport {

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var socket: BluetoothSocket? = null
    private var reader: BufferedReader? = null
    private var writer: OutputStreamWriter? = null

    override val isConnected: Boolean
        get() = socket?.isConnected == true

    @SuppressLint("MissingPermission")
    override suspend fun connect() = withContext(Dispatchers.IO) {
        error("Use connect(address) for Bluetooth transport")
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(address: String) = withContext(Dispatchers.IO) {
        disconnect()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: BluetoothAdapter.getDefaultAdapter()
            ?: throw IllegalStateException("Bluetooth not available")
        val device = adapter.getRemoteDevice(address)
        val connectedSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        adapter.cancelDiscovery()
        connectedSocket.connect()
        socket = connectedSocket
        reader = BufferedReader(InputStreamReader(connectedSocket.inputStream))
        writer = OutputStreamWriter(connectedSocket.outputStream)
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
