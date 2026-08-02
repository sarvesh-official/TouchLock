package com.sarvesh.touchlock

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/**
 * Manages Bluetooth Classic RFCOMM connection to BBK earbuds (Realme/OPPO/OnePlus).
 *
 * Gadgetbridge uses RFCOMM for ALL Oppo/Realme headphones (including Air 5 Pro, Air 6 Pro).
 * No BLE GATT fallback — RFCOMM with the OPO UUID works across the entire BBK ecosystem.
 *
 * Tries two UUIDs and a fixed channel fallback for maximum compatibility:
 *   1. 0000079A (primary OPO UUID — works on most devices)
 *   2. 00001107 (alternate SPP UUID — some devices only register this)
 *   3. Channel 15 (fixed RFCOMM channel — last resort, bypasses SDP)
 */
class BudsConnection(private val context: Context) {

    companion object {
        private const val TAG = "BudsConnection"
        private val OPO_UUID: UUID = UUID.fromString(OpoProtocol.OPO_UUID)
        private const val FIXED_CHANNEL = 15
    }

    private var socket: BluetoothSocket? = null

    /**
     * Find all paired Bluetooth devices that are likely BBK earbuds.
     * Returns a list ordered by likelihood (verified models first).
     */
    fun findBudsDevices(): List<BluetoothDevice> {
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val adapter: BluetoothAdapter = bluetoothManager?.adapter ?: return emptyList()

        if (!adapter.isEnabled) {
            Log.w(TAG, "Bluetooth is not enabled")
            return emptyList()
        }

        val paired = adapter.bondedDevices

        // Only return verified supported brands using shared pattern list
        val supported = paired.filter { dev ->
            val name = dev.name?.lowercase() ?: return@filter false
            OpoProtocol.SUPPORTED_DEVICE_PATTERNS.any { name.contains(it) }
        }

        supported.forEach { Log.i(TAG, "Found supported device: ${it.name} (${it.address})") }
        return supported
    }

    fun findBudsDevice(): BluetoothDevice? = findBudsDevices().firstOrNull()

    /**
     * Check if a Bluetooth device is currently connected (not just paired).
     * Uses reflection to access the hidden isConnected() method.
     */
    fun isDeviceConnected(device: BluetoothDevice): Boolean {
        return try {
            val method = device.javaClass.getMethod("isConnected")
            method.invoke(device) as Boolean
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Find supported earbuds, sorted with connected devices first.
     */
    fun findBudsDevicesSorted(): List<BluetoothDevice> {
        val devices = findBudsDevices()
        val (connected, disconnected) = devices.partition { isDeviceConnected(it) }
        connected.forEach { Log.i(TAG, "CONNECTED: ${it.name} (${it.address})") }
        disconnected.forEach { Log.i(TAG, "Paired but not connected: ${it.name} (${it.address})") }
        return connected + disconnected
    }

    /**
     * Query battery from a specific device only.
     */
    suspend fun queryBatteryForDevice(device: BluetoothDevice): Triple<Int, Int, Int>? = withContext(Dispatchers.IO) {
        Log.i(TAG, "Querying battery for specific device: ${device.name} (${device.address})")
        if (!connectWithRetry(device)) {
            Log.e(TAG, "Failed to connect to ${device.name}")
            return@withContext null
        }
        val out = socket?.outputStream
        val inp = socket?.inputStream
        if (out == null || inp == null) {
            Log.e(TAG, "No stream for ${device.name}")
            disconnect()
            return@withContext null
        }
        try {
            val frame = OpoProtocol.buildBatteryRequestFrame()
            out.write(frame)
            out.flush()
            Log.i(TAG, "Sent battery request to ${device.name}")

            val buf = ByteArray(256)
            var response = byteArrayOf()
            val deadline = System.currentTimeMillis() + 3000
            while (System.currentTimeMillis() < deadline) {
                if (inp.available() > 0) {
                    val n = inp.read(buf)
                    if (n > 0) {
                        response += buf.copyOfRange(0, n)
                        Log.i(TAG, "Read $n bytes: ${OpoProtocol.toHex(buf.copyOfRange(0, n))}")
                    }
                } else {
                    try { Thread.sleep(50) } catch (_: InterruptedException) {}
                }
            }
            disconnect()
            if (response.isEmpty()) {
                Log.i(TAG, "No battery response from ${device.name}")
                return@withContext null
            }
            OpoProtocol.parseBatteryResponse(response)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to query battery from ${device.name}: ${e.message}")
            disconnect()
            null
        }
    }

    /**
     * Connect to the earbuds via RFCOMM. Tries multiple UUIDs and a fixed channel.
     * Returns true on success.
     */
    private fun connectWithRetry(device: BluetoothDevice, maxRetries: Int = 2): Boolean {
        for (attempt in 1..maxRetries) {
            // Try primary UUID
            if (tryConnect(device, OPO_UUID, "primary UUID", attempt)) return true
            // Try fixed channel 15 (bypasses SDP) — faster than alternate UUID
            if (tryConnectChannel(device, FIXED_CHANNEL, "channel $FIXED_CHANNEL", attempt)) return true

            if (attempt < maxRetries) {
                try { Thread.sleep(200) } catch (_: InterruptedException) {}
            }
        }
        Log.e(TAG, "All connect attempts failed for ${device.name}")
        return false
    }

    private fun tryConnect(device: BluetoothDevice, uuid: UUID, label: String, attempt: Int): Boolean {
        return try {
            Log.i(TAG, "Connecting to ${device.name} via $label (attempt $attempt)...")
            socket = device.createRfcommSocketToServiceRecord(uuid)
            // Connect with a timeout — don't block for 5+ seconds on unreachable devices
            val connected = connectWithTimeout(socket!!, timeoutMs = 3000)
            if (connected) {
                Log.i(TAG, "Connected via $label on attempt $attempt!")
                true
            } else {
                try { socket?.close() } catch (_: IOException) {}
                socket = null
                false
            }
        } catch (e: IOException) {
            Log.w(TAG, "$label attempt $attempt failed: ${e.message}")
            try { socket?.close() } catch (_: IOException) {}
            socket = null
            false
        }
    }

    private fun tryConnectChannel(device: BluetoothDevice, channel: Int, label: String, attempt: Int): Boolean {
        return try {
            Log.i(TAG, "Connecting to ${device.name} via $label (attempt $attempt)...")
            val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            socket = method.invoke(device, channel) as BluetoothSocket
            val connected = connectWithTimeout(socket!!, timeoutMs = 3000)
            if (connected) {
                Log.i(TAG, "Connected via $label on attempt $attempt!")
                true
            } else {
                try { socket?.close() } catch (_: IOException) {}
                socket = null
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "$label attempt $attempt failed: ${e.message}")
            try { socket?.close() } catch (_: IOException) {}
            socket = null
            false
        }
    }

    /**
     * Connect to a BluetoothSocket with a timeout.
     * socket.connect() blocks indefinitely on some devices — this runs it on a
     * background thread and aborts if it doesn't complete within the timeout.
     */
    private fun connectWithTimeout(socket: BluetoothSocket, timeoutMs: Long): Boolean {
        var connected = false
        val thread = Thread {
            try {
                socket.connect()
                connected = true
            } catch (e: IOException) {
                Log.w(TAG, "Socket connect failed: ${e.message}")
            }
        }
        thread.isDaemon = true
        thread.start()
        thread.join(timeoutMs)
        if (thread.isAlive) {
            thread.interrupt()
            Log.w(TAG, "Socket connect timed out after ${timeoutMs}ms")
            return false
        }
        return connected
    }

    /**
     * Send a frame to the earbuds. Tries each matched device with retries.
     * Returns the name of the device that succeeded, or null if all failed.
     */
    suspend fun sendFrame(frame: ByteArray): String? = withContext(Dispatchers.IO) {
        sendFrameAndRead(frame, readTimeoutMs = 0)?.first
    }

    /**
     * Send multiple frames sequentially (for multi-slot touch config).
     * Reads and discards ACK responses between frames to prevent buffer overflow
     * on devices that send responses (Air 5 Pro, Air 6 Pro).
     * Returns the device name if all frames sent successfully, null if any failed.
     */
    suspend fun sendFrames(frames: List<ByteArray>): String? = withContext(Dispatchers.IO) {
        if (frames.isEmpty()) return@withContext null

        // Use sorted device list — connected devices first, then selected device
        val devices = findBudsDevicesSorted()
        if (devices.isEmpty()) {
            Log.e(TAG, "No buds device found")
            return@withContext null
        }

        for (device in devices) {
            // Skip devices that aren't connected — don't waste time trying to
            // connect to earbuds that are in the case or out of range
            if (!isDeviceConnected(device)) {
                Log.i(TAG, "Skipping disconnected device: ${device.name}")
                continue
            }
            Log.i(TAG, "Trying device: ${device.name} (${device.address})")
            if (!connectWithRetry(device)) continue
            val out = socket?.outputStream
            val inp = socket?.inputStream
            if (out == null || inp == null) {
                Log.e(TAG, "No stream for ${device.name}")
                disconnect()
                continue
            }

            // Drain any pending data from a previous connection
            drainInput(inp)

            var allSent = true
            for ((i, frame) in frames.withIndex()) {
                try {
                    out.write(frame)
                    out.flush()
                    Log.i(TAG, "Sent frame ${i + 1}/${frames.size} (${frame.size} bytes): ${OpoProtocol.toHex(frame)}")
                    // Wait for the bud to process and send ACK, then drain it
                    Thread.sleep(50)
                    drainInput(inp)
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to send frame ${i + 1} to ${device.name}: ${e.message}")
                    allSent = false
                    break
                }
            }

            // Wait a bit for the buds to process the last frame before disconnecting
            Thread.sleep(100)
            disconnect()
            if (allSent) {
                Log.i(TAG, "All ${frames.size} frames sent to ${device.name}")
                return@withContext device.name
            }
        }

        Log.e(TAG, "Failed to send all frames")
        null
    }

    /**
     * Read and discard any pending data from the input stream.
     * Prevents buffer overflow on devices that send ACK responses.
     */
    private fun drainInput(inp: java.io.InputStream) {
        try {
            val buf = ByteArray(256)
            while (inp.available() > 0) {
                val n = inp.read(buf)
                if (n > 0) {
                    Log.i(TAG, "Drained $n bytes: ${OpoProtocol.toHex(buf.copyOfRange(0, n))}")
                } else {
                    break
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Error draining input: ${e.message}")
        }
    }

    /**
     * Send a frame and optionally read a response.
     */
    suspend fun sendFrameAndRead(frame: ByteArray, readTimeoutMs: Long = 0): Pair<String, ByteArray>? = withContext(Dispatchers.IO) {
        val devices = findBudsDevicesSorted()
        if (devices.isEmpty()) {
            Log.e(TAG, "No buds device found")
            return@withContext null
        }

        for (device in devices) {
            if (!isDeviceConnected(device)) {
                Log.i(TAG, "Skipping disconnected device: ${device.name}")
                continue
            }
            Log.i(TAG, "Trying device: ${device.name} (${device.address})")
            if (!connectWithRetry(device)) continue
            val out = socket?.outputStream
            val inp = socket?.inputStream
            if (out == null || inp == null) {
                Log.e(TAG, "No stream for ${device.name}")
                disconnect()
                continue
            }
            try {
                out.write(frame)
                out.flush()
                Log.i(TAG, "Sent ${frame.size} bytes to ${device.name}: ${OpoProtocol.toHex(frame)}")

                var response = byteArrayOf()
                if (readTimeoutMs > 0) {
                    Log.i(TAG, "Waiting ${readTimeoutMs}ms for response...")
                    val buf = ByteArray(256)
                    val deadline = System.currentTimeMillis() + readTimeoutMs
                    while (System.currentTimeMillis() < deadline) {
                        if (inp.available() > 0) {
                            val n = inp.read(buf)
                            if (n > 0) {
                                response += buf.copyOfRange(0, n)
                                Log.i(TAG, "Read $n bytes: ${OpoProtocol.toHex(buf.copyOfRange(0, n))}")
                            }
                        } else {
                            try { Thread.sleep(50) } catch (_: InterruptedException) {}
                        }
                    }
                }
                disconnect()
                return@withContext device.name to response
            } catch (e: IOException) {
                Log.e(TAG, "Failed to send to ${device.name}: ${e.message}")
                disconnect()
                continue
            }
        }

        Log.e(TAG, "All ${devices.size} devices failed to connect")
        null
    }

    /**
     * Query battery levels from the earbuds.
     */
    suspend fun queryBattery(): Triple<Int, Int, Int>? = withContext(Dispatchers.IO) {
        val frame = OpoProtocol.buildBatteryRequestFrame()
        val result = sendFrameAndRead(frame, readTimeoutMs = 3000) ?: return@withContext null
        val (_, response) = result
        if (response.isEmpty()) {
            Log.i(TAG, "No battery response from buds")
            return@withContext null
        }
        OpoProtocol.parseBatteryResponse(response)
    }

    fun disconnect() {
        try {
            socket?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Error closing socket: ${e.message}")
        }
        socket = null
        Log.i(TAG, "Disconnected")
    }
}
