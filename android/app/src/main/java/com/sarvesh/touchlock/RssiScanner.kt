package com.sarvesh.touchlock

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.channels.awaitClose

/**
 * Reads RSSI (signal strength) from earbuds.
 *
 * Two modes:
 * 1. BLE scan RSSI — for direction finding (body shielding). Advertising RSSI
 *    is NOT power-controlled, so body shielding works: signal strongest when
 *    facing the device, weakest when facing away.
 * 2. GATT readRemoteRssi — for proximity display only. Connection RSSI IS
 *    power-controlled, so it's more stable for distance but wrong for direction.
 *
 * RSSI values (typical):
 *   -30 dBm = very close (less than 1m)
 *   -50 dBm = close (1-2m)
 *   -70 dBm = medium (5-10m)
 *   -90 dBm = far (10m+)
 */
class RssiScanner(private val context: Context) {

    companion object {
        private const val TAG = "RssiScanner"
    }

    /**
     * BLE scan that emits (mac, name, rssi) for all nearby devices.
     * Uses advertising RSSI which is NOT power-controlled — ideal for body shielding.
     */
    fun scanAllRssi(): Flow<DeviceRssi> = callbackFlow {
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val adapter: BluetoothAdapter? = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            close()
            return@callbackFlow
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            close()
            return@callbackFlow
        }

        val seenDevices = mutableMapOf<String, DeviceRssi>()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                for (result in results) handleResult(result)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE scan failed: errorCode=$errorCode")
                close()
            }

            fun handleResult(result: ScanResult) {
                val device = result.device ?: return
                val mac = device.address
                val rssi = result.rssi
                var name = try { device.name } catch (e: SecurityException) { null }
                if (name.isNullOrBlank()) name = seenDevices[mac]?.name ?: "Unknown"

                val entry = DeviceRssi(mac = mac, name = name, rssi = rssi)
                seenDevices[mac] = entry
                trySend(entry)
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        try {
            scanner.startScan(null, settings, scanCallback)
            Log.i(TAG, "BLE scan started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Scan permission denied: ${e.message}")
            close()
            return@callbackFlow
        }

        awaitClose {
            try {
                scanner.stopScan(scanCallback)
                Log.i(TAG, "BLE scan stopped")
            } catch (e: SecurityException) {
                Log.w(TAG, "Error stopping scan: ${e.message}")
            }
        }
    }

    /**
     * Track the strongest nearby BLE device's RSSI in real-time.
     * Auto-selects the device with the strongest signal (likely the earbuds if close).
     * Emits (mac, name, rssi) tuples.
     *
     * Uses BLE advertising RSSI — NOT power-controlled, so body shielding works.
     *
     * Selection logic: scans for 3 seconds, counts how many times each device appears,
     * then locks onto the device with the highest RSSI that appeared at least 3 times.
     * This filters out one-off packets from passing devices and handles BLE MAC rotation.
     */
    fun trackStrongestDevice(intervalMs: Long = 300): Flow<DeviceRssi> = callbackFlow {
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val adapter: BluetoothAdapter? = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            close()
            return@callbackFlow
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            close()
            return@callbackFlow
        }

        val deviceRssiMap = mutableMapOf<String, DeviceRssi>()
        val deviceSightings = mutableMapOf<String, Int>() // how many times we've seen each MAC
        var trackedMac: String? = null
        var scanStartTime = System.currentTimeMillis()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                for (result in results) handleResult(result)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Track scan failed: errorCode=$errorCode")
                close()
            }

            fun handleResult(result: ScanResult) {
                val device = result.device ?: return
                val mac = device.address
                val rssi = result.rssi
                var name = try { device.name } catch (e: SecurityException) { null }
                if (name.isNullOrBlank()) name = deviceRssiMap[mac]?.name ?: "Unknown"

                val entry = DeviceRssi(mac = mac, name = name, rssi = rssi)
                deviceRssiMap[mac] = entry
                deviceSightings[mac] = (deviceSightings[mac] ?: 0) + 1

                // Selection phase: after 3 seconds of scanning, pick the strongest
                // device that we've seen at least 3 times (filters one-off packets)
                if (trackedMac == null) {
                    val elapsed = System.currentTimeMillis() - scanStartTime
                    if (elapsed > 3000) {
                        // Find strongest device seen at least 3 times
                        val candidates = deviceSightings.filter { it.value >= 3 }
                        if (candidates.isNotEmpty()) {
                            val strongestMac = candidates.keys.maxByOrNull { mac ->
                                deviceRssiMap[mac]?.rssi ?: Int.MIN_VALUE
                            }
                            if (strongestMac != null) {
                                trackedMac = strongestMac
                                val tracked = deviceRssiMap[strongestMac]
                                Log.i(TAG, "Locked onto: ${tracked?.name} ($strongestMac) RSSI=${tracked?.rssi} sightings=${deviceSightings[strongestMac]}")
                            }
                        } else if (elapsed > 6000) {
                            // Fallback after 6s: just pick strongest even if few sightings
                            val strongest = deviceRssiMap.maxByOrNull { it.value.rssi }
                            if (strongest != null) {
                                trackedMac = strongest.key
                                Log.i(TAG, "Fallback lock: ${strongest.value.name} (${strongest.key}) RSSI=${strongest.value.rssi}")
                            }
                        }
                    }
                }

                // Emit
                if (trackedMac != null) {
                    // If tracked MAC hasn't been seen recently, it may have rotated.
                    // Check if there's a new MAC with very similar RSSI (within 5 dB)
                    val tracked = deviceRssiMap[trackedMac]
                    if (tracked != null) {
                        trySend(tracked)
                    }
                    // Handle MAC rotation: if tracked device hasn't been seen in a while
                    // and a new device appears with similar RSSI, switch to it
                    if (tracked == null || (System.currentTimeMillis() - scanStartTime > 5000 && deviceSightings[trackedMac] == 1)) {
                        val recentStrong = deviceRssiMap.filter { it.key != trackedMac }
                            .maxByOrNull { it.value.rssi }
                        if (recentStrong != null && (tracked == null || recentStrong.value.rssi >= tracked.rssi - 5)) {
                            trackedMac = recentStrong.key
                            Log.i(TAG, "MAC rotation: switched to ${recentStrong.value.name} (${recentStrong.key}) RSSI=${recentStrong.value.rssi}")
                            trySend(recentStrong.value)
                        }
                    }
                } else {
                    // Still scanning — emit the current strongest
                    val strongest = deviceRssiMap.maxByOrNull { it.value.rssi }
                    if (strongest != null) {
                        trySend(strongest.value)
                    }
                }
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        try {
            scanner.startScan(null, settings, scanCallback)
            Log.i(TAG, "Track scan started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Track scan permission denied: ${e.message}")
            close()
            return@callbackFlow
        }

        awaitClose {
            try {
                scanner.stopScan(scanCallback)
                Log.i(TAG, "Track scan stopped")
            } catch (e: SecurityException) {
                Log.w(TAG, "Error stopping track scan: ${e.message}")
            }
        }
    }

    /**
     * Read RSSI from a connected device using GATT readRemoteRssi().
     * This is power-controlled — use for proximity display, NOT direction finding.
     */
    fun readConnectedRssi(
        deviceMac: String,
        intervalMs: Long = 500,
    ): Flow<Int> = flow {
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val adapter: BluetoothAdapter? = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) return@flow

        val device = try {
            adapter.bondedDevices.find { it.address.equals(deviceMac, ignoreCase = true) }
        } catch (e: SecurityException) { null }

        if (device == null) {
            Log.e(TAG, "Device $deviceMac not found in bonded devices")
            return@flow
        }

        var gatt: BluetoothGatt? = null
        var lastRssi: Int? = null
        var connected = false

        val gattCallback = object : BluetoothGattCallback() {
            override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    lastRssi = rssi
                }
            }

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) connected = true
            }
        }

        try {
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_AUTO)
        } catch (e: SecurityException) {
            return@flow
        }

        delay(2000)
        if (!connected) {
            try { gatt.close() } catch (_: Exception) {}
            return@flow
        }

        try {
            while (true) {
                lastRssi?.let { emit(it) }
                try { gatt.readRemoteRssi() } catch (e: SecurityException) {}
                delay(intervalMs)
            }
        } finally {
            try { gatt.close() } catch (_: Exception) {}
        }
    }

    /**
     * Read RSSI from connected earbuds via GATT (power-controlled, for proximity only).
     */
    fun readConnectedEarbudsRssi(intervalMs: Long = 500): Flow<Int> = flow {
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val adapter: BluetoothAdapter? = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) return@flow

        val keywords = listOf("realme buds", "buds air", "nord buds", "oneplus", "enco", "oppo", "cmf")
        val bondedBuds = try {
            adapter.bondedDevices.filter { device ->
                val name = device.name?.lowercase() ?: return@filter false
                keywords.any { name.contains(it) }
            }
        } catch (e: SecurityException) { emptyList() }

        for (device in bondedBuds) {
            Log.i(TAG, "Trying GATT for ${device.name} (${device.address})")
            var gatt: BluetoothGatt? = null
            var lastRssi: Int? = null
            var connected = false

            val gattCallback = object : BluetoothGattCallback() {
                override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) lastRssi = rssi
                }
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) connected = true
                }
            }

            try {
                gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_AUTO)
            } catch (e: SecurityException) { continue }

            delay(2000)
            if (!connected) {
                try { gatt.close() } catch (_: Exception) {}
                continue
            }

            Log.i(TAG, "GATT connected to ${device.name}!")
            try {
                while (true) {
                    lastRssi?.let { emit(it) }
                    try { gatt.readRemoteRssi() } catch (e: SecurityException) {}
                    delay(intervalMs)
                }
            } finally {
                try { gatt.close() } catch (_: Exception) {}
            }
        }
    }

    data class DeviceRssi(
        val mac: String,
        val name: String,
        val rssi: Int,
    )
}
