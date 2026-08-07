package com.sarvesh.touchlock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScanScreen(
    onBack: () -> Unit,
    onDeviceSelected: (mac: String, name: String) -> Unit,
) {
    val context = LocalContext.current
    val scanner = remember { RssiScanner(context) }
    val devices = remember { MutableStateFlow<Map<String, RssiScanner.DeviceRssi>>(emptyMap()) }

    LaunchedEffect(Unit) {
        scanner.scanAllRssi().collect { device ->
            devices.update { current ->
                current + (device.mac to device)
            }
        }
    }

    val deviceMap by devices.collectAsStateWithLifecycle()
    val sortedDevices = deviceMap.values.sortedByDescending { it.rssi }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Locate Device", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = {
                        Haptics.click()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            ) {
                Icon(
                    Icons.Filled.Radar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (sortedDevices.isEmpty()) "Scanning for nearby devices..."
                           else "${sortedDevices.size} device${if (sortedDevices.size > 1) "s" else ""} found",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (sortedDevices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(sortedDevices, key = { it.mac }) { device ->
                        DeviceScanRow(
                            device = device,
                            onClick = {
                                Haptics.click()
                                onDeviceSelected(device.mac, device.name)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceScanRow(
    device: RssiScanner.DeviceRssi,
    onClick: () -> Unit,
) {
    val signalStrength = when {
        device.rssi >= -50 -> "Very Close"
        device.rssi >= -60 -> "Close"
        device.rssi >= -70 -> "Nearby"
        else -> "Far"
    }
    val signalColor = when {
        device.rssi >= -60 -> MaterialTheme.colorScheme.primary
        device.rssi >= -70 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(signalColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Bluetooth,
                contentDescription = null,
                tint = signalColor,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = signalStrength,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "${device.rssi} dBm",
            fontSize = 12.sp,
            color = signalColor,
            fontWeight = FontWeight.Medium,
        )
    }
}
