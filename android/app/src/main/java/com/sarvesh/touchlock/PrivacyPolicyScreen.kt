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
import androidx.compose.ui.text.style.TextAlign
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Icon(
                Icons.Filled.PrivacyTip,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "TouchLock Privacy Policy",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Last updated: August 2026",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle("Overview")
            BodyText("This Privacy Policy explains how TouchLock handles your information when you use the app. TouchLock is a utility app that lets you lock touch controls on your Realme, OnePlus, Nord, or OPPO earbuds. We built TouchLock with a simple principle: your data stays on your phone.")

            SectionTitle("Data We Collect")
            BodyText("TouchLock does not collect, store, or transmit any personal or sensitive user data. There is no server, no cloud service, no analytics, no telemetry, no advertising SDK, and no tracking. The app does not create user accounts and does not require you to sign in.")
            BodyText("The only information stored locally on your device is: your earbud lock state (whether left, right, or both buds are locked), your gesture settings (which action each tap gesture performs), and the name of the last connected earbud device. This information never leaves your device.")

            SectionTitle("Permissions")
            BodyText("TouchLock requests the following permissions, all strictly for functionality:")
            Spacer(modifier = Modifier.height(8.dp))
            PermissionRow("BLUETOOTH_SCAN", "Discover nearby supported earbuds for Find Nearby")
            PermissionRow("BLUETOOTH_CONNECT", "Send touch lock commands to your earbuds")
            PermissionRow("VIBRATE", "Provide haptic feedback when toggling lock")
            PermissionRow("BLUETOOTH (legacy)", "Bluetooth access on Android 11 and below")
            Spacer(modifier = Modifier.height(8.dp))
            BodyText("No permission is used to collect data. All Bluetooth communication happens directly between your phone and your earbuds. No data is sent to any server.")

            SectionTitle("Third-Party Services")
            BodyText("TouchLock uses Google Play Billing for the optional Supporter purchase. Google Play processes the transaction, and we receive a confirmation that the purchase was made to unlock the supporter badge. We do not receive or store your payment details, credit card information, or any financial data. Google's privacy policy governs the billing process.")
            BodyText("TouchLock does not include any advertising SDKs, analytics SDKs, crash reporting SDKs, or tracking libraries.")

            SectionTitle("Data Security")
            BodyText("Since TouchLock does not collect or transmit any data, there is no data at risk. All local data (lock state, gesture settings, device name) is stored in your app's private storage on your device, which is sandboxed by Android and not accessible to other apps.")

            SectionTitle("Data Sharing")
            BodyText("TouchLock does not share any data with any third party, because there is no data to share. We do not sell, rent, or monetize any information.")

            SectionTitle("Children's Privacy")
            BodyText("TouchLock is not directed at children under 13 and does not knowingly collect any data from anyone. Since the app collects no data at all, it is safe for users of all ages.")

            SectionTitle("Your Rights")
            BodyText("Since TouchLock stores no data outside your device, there is no data to access, export, or delete on a server. If you uninstall the app, all local data (lock state, gesture settings) is automatically removed by Android.")

            SectionTitle("Open Source")
            BodyText("TouchLock is fully open source. You can review the complete source code at github.com/sarvesh-official/TouchLock to verify these claims independently.")

            SectionTitle("Changes to This Policy")
            BodyText("If we ever change this Privacy Policy, we will update this page within the app and on the GitHub repository. Since TouchLock does not collect data, we do not expect any changes that would affect your privacy.")

            SectionTitle("Contact")
            BodyText("If you have questions about this Privacy Policy, you can open an issue on the GitHub repository at github.com/sarvesh-official/TouchLock.")

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "TouchLock collects no data. Your privacy is not a feature, it's the default.",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
        modifier = Modifier.fillMaxWidth(),
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
        modifier = Modifier.fillMaxWidth(),
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
