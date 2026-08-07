package com.sarvesh.touchlock

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy", fontWeight = FontWeight.ExtraBold) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
        ) {
            Icon(
                Icons.Filled.PrivacyTip,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(48.dp)
                    .padding(top = 8.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "TouchLock Privacy Policy",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Last updated: August 2026",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle("Data We Collect")
            BodyText("TouchLock does not collect, store, or transmit any personal data. The app operates entirely on your device. There is no server, no cloud service, no analytics, no telemetry, and no advertising SDK.")

            SectionTitle("Permissions")
            BodyText("The permissions TouchLock requests are strictly for functionality:")
            Spacer(modifier = Modifier.height(8.dp))
            PermissionRow("BLUETOOTH_SCAN", "Discover nearby earbuds for Find Nearby")
            PermissionRow("BLUETOOTH_CONNECT", "Send touch lock commands to your earbuds")
            PermissionRow("VIBRATE", "Haptic feedback when toggling lock")
            PermissionRow("BLUETOOTH (legacy)", "Bluetooth access for Android 11 and below")
            Spacer(modifier = Modifier.height(8.dp))
            BodyText("No permission is used to collect data. All Bluetooth communication is directly between your phone and your earbuds. No data leaves your device.")

            SectionTitle("In-App Purchases")
            BodyText("TouchLock offers a one-time Supporter purchase via Google Play Billing. The purchase is processed by Google Play. We receive a notification that the purchase was made to unlock the supporter badge, but we do not receive or store your payment information.")

            SectionTitle("Data Storage")
            BodyText("Earbuds pairing info, gesture settings, and touch lock state are all stored locally on your device. Nothing is transmitted anywhere.")

            SectionTitle("Open Source")
            BodyText("TouchLock is fully open source. You can review the complete source code at github.com/sarvesh-official/TouchLock")

            SectionTitle("Children's Privacy")
            BodyText("TouchLock is not directed at children under 13. We do not knowingly collect any data from anyone.")

            SectionTitle("Contact")
            BodyText("For questions about this privacy policy, open an issue on the GitHub repository.")

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "TouchLock collects no data. Your privacy is not a feature — it's the default.",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PermissionRow(permission: String, purpose: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = permission,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(140.dp),
        )
        Text(
            text = purpose,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}
