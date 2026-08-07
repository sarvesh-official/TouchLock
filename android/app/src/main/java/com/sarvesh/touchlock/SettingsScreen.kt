package com.sarvesh.touchlock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
fun SettingsScreen(
    onBack: () -> Unit,
    onGestureSettingsClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onAddQsTile: () -> Unit,
    tileAdded: Boolean,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            // ── Controls section ──
            SectionLabel("CONTROLS")

            SettingsRow(
                icon = Icons.Filled.TouchApp,
                title = "Touch Controls",
                subtitle = "Choose what each tap gesture does",
                onClick = onGestureSettingsClick,
            )

            SettingsRow(
                icon = Icons.Filled.Settings,
                title = if (tileAdded) "Quick Settings Tile" else "Add Quick Settings Tile",
                subtitle = if (tileAdded) "Added, toggle lock from quick settings"
                           else "Add Touch Lock to your quick settings panel for easy access",
                trailing = if (tileAdded) {
                    { Icon(Icons.Filled.Verified, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
                } else null,
                onClick = { if (!tileAdded) onAddQsTile() },
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── About section ──
            SectionLabel("ABOUT")

            SettingsRow(
                icon = Icons.Filled.PrivacyTip,
                title = "Privacy Policy",
                subtitle = "TouchLock collects no data",
                onClick = onPrivacyPolicyClick,
            )

            SettingsRow(
                icon = Icons.Filled.Star,
                title = "Support TouchLock",
                subtitle = "Become a Supporter for \$2.99",
                onClick = { },
            )

            // Version info
            Spacer(modifier = Modifier.height(8.dp))
            val version = remember {
                try {
                    val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
                    "Version ${pkg.versionName}"
                } catch (e: Exception) {
                    "Version 1.0.0"
                }
            }
            Text(
                text = version,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
            Text(
                text = "Open source, github.com/sarvesh-official/TouchLock",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
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
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
