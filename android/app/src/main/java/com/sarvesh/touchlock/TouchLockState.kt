package com.sarvesh.touchlock

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persistent state for the touch lock feature.
 * Shared between the MainActivity and the Quick Settings tile service.
 *
 * Tracks left and right bud lock state independently so the user can
 * lock just one bud (e.g. the one they sleep on).
 */
object TouchLockState {
    private const val PREFS_NAME = "touchlock_state"
    private const val KEY_LEFT_LOCKED = "left_locked"
    private const val KEY_RIGHT_LOCKED = "right_locked"
    private const val KEY_DEVICE = "last_device_name"

    private val _leftLocked = MutableStateFlow(false)
    val leftLocked: StateFlow<Boolean> = _leftLocked.asStateFlow()

    private val _rightLocked = MutableStateFlow(false)
    val rightLocked: StateFlow<Boolean> = _rightLocked.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    data class DeviceInfo(val name: String, val address: String)
    private val _availableDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val availableDevices: StateFlow<List<DeviceInfo>> = _availableDevices.asStateFlow()

    private val _selectedDeviceAddress = MutableStateFlow<String?>(null)
    val selectedDeviceAddress: StateFlow<String?> = _selectedDeviceAddress.asStateFlow()

    fun setAvailableDevices(devices: List<DeviceInfo>) {
        _availableDevices.value = devices
    }

    fun selectDevice(address: String) {
        _selectedDeviceAddress.value = address
    }

    data class Battery(val left: Int = -1, val right: Int = -1, val case: Int = -1)
    private val _battery = MutableStateFlow(Battery())
    val battery: StateFlow<Battery> = _battery.asStateFlow()

    fun setBattery(left: Int, right: Int, case: Int) {
        _battery.value = Battery(left, right, case)
    }

    fun clearBattery() {
        _battery.value = Battery()
    }

    fun setConnected(connected: Boolean) {
        _connected.value = connected
        if (!connected) clearBattery()
    }

    fun isLeftLocked(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_LEFT_LOCKED, false)
    }

    fun isRightLocked(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_RIGHT_LOCKED, false)
    }

    fun setLeftLocked(context: Context, locked: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LEFT_LOCKED, locked)
            .apply()
        _leftLocked.value = locked
    }

    fun setRightLocked(context: Context, locked: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RIGHT_LOCKED, locked)
            .apply()
        _rightLocked.value = locked
    }

    fun setBothLocked(context: Context, locked: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LEFT_LOCKED, locked)
            .putBoolean(KEY_RIGHT_LOCKED, locked)
            .apply()
        _leftLocked.value = locked
        _rightLocked.value = locked
    }

    fun getLastDevice(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEVICE, null)
    }

    fun setLastDevice(context: Context, name: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEVICE, name)
            .apply()
        _deviceName.value = name
    }

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _leftLocked.value = prefs.getBoolean(KEY_LEFT_LOCKED, false)
        _rightLocked.value = prefs.getBoolean(KEY_RIGHT_LOCKED, false)
        _deviceName.value = prefs.getString(KEY_DEVICE, null)
    }
}
