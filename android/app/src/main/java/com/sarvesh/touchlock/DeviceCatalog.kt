package com.sarvesh.touchlock

import android.content.Context

data class DeviceModel(
    val name: String,
    val brand: String,
    val bluetoothPattern: String,
)

data class VerifiedDevice(
    val name: String,
    val brand: String,
)

object DeviceCatalog {

    val KNOWN_DEVICES = listOf(
        // Realme Buds Air series
        DeviceModel("Realme Buds Air", "Realme", "realme buds air"),
        DeviceModel("Realme Buds Air 2", "Realme", "realme buds air 2"),
        DeviceModel("Realme Buds Air 3", "Realme", "realme buds air 3"),
        DeviceModel("Realme Buds Air 5 Pro", "Realme", "realme buds air 5"),
        DeviceModel("Realme Buds Air 7", "Realme", "realme buds air 7"),
        DeviceModel("Realme Buds Air 7", "Realme", "realme buds air7"),
        // Realme Buds series
        DeviceModel("Realme Buds Q", "Realme", "realme buds q"),
        DeviceModel("Realme Buds 2", "Realme", "realme buds 2"),
        DeviceModel("Realme Buds 3", "Realme", "realme buds 3"),
        // OnePlus Buds
        DeviceModel("OnePlus Buds", "OnePlus", "oneplus buds"),
        DeviceModel("OnePlus Buds 3", "OnePlus", "oneplus buds 3"),
        DeviceModel("OnePlus Buds Pro", "OnePlus", "oneplus buds pro"),
        DeviceModel("OnePlus Buds Pro 2", "OnePlus", "oneplus buds pro 2"),
        DeviceModel("OnePlus Buds Pro 3", "OnePlus", "oneplus buds pro 3"),
        // OnePlus Nord Buds
        DeviceModel("OnePlus Nord Buds", "OnePlus", "nord buds"),
        DeviceModel("OnePlus Nord Buds 2", "OnePlus", "nord buds 2"),
        DeviceModel("OnePlus Nord Buds 3", "OnePlus", "nord buds 3"),
        // OPPO Enco series
        DeviceModel("OPPO Enco Buds", "OPPO", "enco buds"),
        DeviceModel("OPPO Enco Buds3", "OPPO", "enco buds3"),
        DeviceModel("OPPO Enco W31", "OPPO", "enco w31"),
        DeviceModel("OPPO Enco W51", "OPPO", "enco w51"),
        DeviceModel("OPPO Enco X3", "OPPO", "enco x3"),
        DeviceModel("OPPO Enco X3s", "OPPO", "enco x3s"),
        DeviceModel("OPPO Enco Air", "OPPO", "enco air"),
        DeviceModel("OPPO Enco Air5 Pro", "OPPO", "enco air5"),
    )

    val BRANDS = listOf("Realme", "OnePlus", "OPPO")

    private const val PREFS_NAME = "device_catalog"
    private const val KEY_VERIFIED = "user_verified_devices"

    fun isKnownDevice(deviceName: String): Boolean {
        val lower = deviceName.lowercase()
        return KNOWN_DEVICES.any { lower.contains(it.bluetoothPattern) }
    }

    fun getKnownDevicesByBrand(): Map<String, List<DeviceModel>> {
        return KNOWN_DEVICES.groupBy { it.brand }
    }

    fun getUserVerifiedDevices(context: Context): List<VerifiedDevice> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_VERIFIED, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("||").mapNotNull { entry ->
            val parts = entry.split("::")
            if (parts.size == 2) VerifiedDevice(parts[0], parts[1]) else null
        }
    }

    fun addUserVerifiedDevice(context: Context, name: String, brand: String) {
        val current = getUserVerifiedDevices(context).toMutableList()
        if (current.none { it.name.equals(name, ignoreCase = true) }) {
            current.add(VerifiedDevice(name, brand))
            val raw = current.joinToString("||") { "${it.name}::${it.brand}" }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_VERIFIED, raw).apply()
        }
    }

    fun hasUserVerifiedDevice(context: Context, name: String): Boolean {
        return getUserVerifiedDevices(context).any { it.name.equals(name, ignoreCase = true) }
    }

    fun guessBrand(deviceName: String): String {
        val lower = deviceName.lowercase()
        return when {
            lower.contains("realme") -> "Realme"
            lower.contains("oneplus") || lower.contains("nord") -> "OnePlus"
            lower.contains("oppo") || lower.contains("enco") -> "OPPO"
            else -> "Unknown"
        }
    }
}
