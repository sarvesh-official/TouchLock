package com.sarvesh.touchlock

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupporterSheet(
    billing: SupporterBilling,
    onDismiss: () -> Unit,
) {
    val isSupporter by billing.isSupporter.collectAsStateWithLifecycle()
    val productDetails by billing.productDetails.collectAsStateWithLifecycle()
    val flowState by billing.purchaseFlowState.collectAsStateWithLifecycle()

    // Reset flow state when sheet opens
    LaunchedEffect(Unit) { billing.resetFlowState() }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            billing.resetFlowState()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isSupporter) {
                // Already a supporter
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "You're a Supporter",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Thank you for supporting BudFreeze. Your contribution keeps this project alive.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(20.dp))

                val context = LocalContext.current
                TouchLockSecondaryButton(
                    text = "Priority Feature Requests",
                    icon = Icons.Filled.Code,
                    onClick = {
                        Haptics.click()
                        val githubUrl = "https://github.com/sarvesh-official/BudFreeze/issues/new?labels=priority-supporter&title=Feature+Request"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl)))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // Purchase UI
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Support BudFreeze",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "BudFreeze is free and open source. If it's made your life easier, consider supporting development with a one-time purchase.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Supporter benefits
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SupporterBenefit(icon = Icons.Filled.Palette, text = "Custom accent colors")
                    SupporterBenefit(icon = Icons.Filled.Devices, text = "Find any Bluetooth device")
                    SupporterBenefit(icon = Icons.Filled.Favorite, text = "Filled heart badge")
                    SupporterBenefit(icon = Icons.Filled.Code, text = "Priority feature requests")
                }

                Spacer(modifier = Modifier.height(24.dp))

                val price = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice ?: "$2.99"
                val context = LocalContext.current

                AnimatedContent(
                    targetState = flowState,
                    transitionSpec = {
                        (fadeIn(tween(200)) togetherWith fadeOut(tween(150)))
                    },
                    label = "purchaseState",
                ) { state ->
                    when (state) {
                        PurchaseFlowState.Idle -> {
                            TouchLockButton(
                                text = "Become a Supporter, $price",
                                onClick = {
                                    val activity = context as? android.app.Activity
                                    activity?.let { billing.launchPurchaseFlow(it) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        PurchaseFlowState.Success -> {
                            Text(
                                "Thank you! You're now a Supporter.",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                            )
                        }
                        PurchaseFlowState.Cancelled -> {
                            Text(
                                "Purchase cancelled.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        is PurchaseFlowState.Error -> {
                            Text(
                                state.message,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SupporterBenefit(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
