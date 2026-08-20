package com.sarvesh.touchlock

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help & Support", fontWeight = FontWeight.ExtraBold) },
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
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Supported Devices ──
            HelpSection(
                icon = Icons.Filled.Headphones,
                title = "Supported Earbuds",
            ) {
                Text(
                    "BudFreeze works with earbuds that use the OPO Bluetooth protocol. Here are the known supported models:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))

                val byBrand = DeviceCatalog.getKnownDevicesByBrand()
                byBrand.forEach { (brand, models) ->
                    Text(
                        text = brand,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                    models.forEach { model ->
                        Row(
                            modifier = Modifier.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                model.name,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                // User-verified devices
                val userVerified = DeviceCatalog.getUserVerifiedDevices(context)
                if (userVerified.isNotEmpty()) {
                    Text(
                        text = "User-Verified",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                    userVerified.forEach { device ->
                        Row(
                            modifier = Modifier.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "${device.name} (${device.brand})",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "New models are added as users report them working. If your earbuds aren't listed, try using BudFreeze — they might work! If they do, report it to help other users.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Connection Issues ──
            HelpSection(
                icon = Icons.Filled.BluetoothDisabled,
                title = "Can't Connect to Earbuds?",
            ) {
                HelpTroubleshootItem(
                    step = "1",
                    text = "Make sure earbuds are out of the case and charged",
                )
                HelpTroubleshootItem(
                    step = "2",
                    text = "Pair earbuds in your phone's Bluetooth settings first",
                )
                HelpTroubleshootItem(
                    step = "3",
                    text = "Force-stop Realme Link app if installed (see below)",
                )
                HelpTroubleshootItem(
                    step = "4",
                    text = "Try locking again — connection takes a few seconds",
                )
            }

            // ── Realme Link Conflict ──
            HelpSection(
                icon = Icons.Filled.Warning,
                title = "Realme Link Conflict",
            ) {
                Text(
                    "The Realme Link app and BudFreeze both try to control your earbuds over Bluetooth. If Realme Link is running in the background, it can block BudFreeze from connecting.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "To fix this:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                HelpTroubleshootItem(
                    step = "1",
                    text = "Go to phone Settings > Apps > Realme Link",
                )
                HelpTroubleshootItem(
                    step = "2",
                    text = "Tap Force Stop",
                )
                HelpTroubleshootItem(
                    step = "3",
                    text = "Open BudFreeze and try again",
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "You can re-open Realme Link later if you need to change earbud settings. Just force-stop it again before using BudFreeze.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Lock Not Working ──
            HelpSection(
                icon = Icons.Filled.Lock,
                title = "Lock/Unlock Not Working?",
            ) {
                HelpTroubleshootItem(
                    step = "1",
                    text = "Ensure earbuds are connected (check the status chip at top)",
                )
                HelpTroubleshootItem(
                    step = "2",
                    text = "Try the main Lock Both button instead of individual toggles",
                )
                HelpTroubleshootItem(
                    step = "3",
                    text = "Restart Bluetooth on your phone and try again",
                )
                HelpTroubleshootItem(
                    step = "4",
                    text = "If gestures don't work after unlocking, check Touch Controls in Settings",
                )
            }

            // ── Quick Settings Tile ──
            HelpSection(
                icon = Icons.Filled.Bluetooth,
                title = "Quick Settings Tile",
            ) {
                Text(
                    "Add the BudFreeze tile to your Quick Settings panel for instant access without opening the app:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                HelpTroubleshootItem(
                    step = "1",
                    text = "Swipe down from the top of your screen to open Quick Settings",
                )
                HelpTroubleshootItem(
                    step = "2",
                    text = "Tap the Edit (pencil) icon",
                )
                HelpTroubleshootItem(
                    step = "3",
                    text = "Drag the BudFreeze tile to your active tiles",
                )
                HelpTroubleshootItem(
                    step = "4",
                    text = "Tap the tile to lock/unlock both buds instantly",
                )
            }

            // ── Report Issue ──
            HelpSection(
                icon = Icons.Filled.Link,
                title = "Still Need Help?",
            ) {
                Text(
                    "If BudFreeze isn't working with your earbuds, report it on GitHub so we can investigate and potentially add support:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                TouchLockButton(
                    text = "Report Issue on GitHub",
                    icon = Icons.Filled.HelpOutline,
                    onClick = {
                        Haptics.click()
                        val url = "https://github.com/sarvesh-official/BudFreeze/issues/new?labels=device-support&title=Device+not+working"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    height = 46.dp,
                )
            }
        }
    }
}

@Composable
private fun HelpSection(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun HelpTroubleshootItem(
    step: String,
    text: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = step,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
