package com.sarvesh.touchlock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
) {
    val pages = listOf(
        OnboardingPage(
            icon = Icons.Filled.Headphones,
            title = "Welcome to BudFreeze",
            description = "Lock and unlock touch controls on your earbuds directly from your phone. No more accidental taps while working out or sleeping.",
            bullets = listOf(
                "Works with Realme Buds, Nord Buds, OnePlus Buds, and OPPO Enco",
                "Free and open source",
                "No ads, no tracking, no data collection",
            ),
        ),
        OnboardingPage(
            icon = Icons.Filled.Bluetooth,
            title = "Pair Your Earbuds First",
            description = "BudFreeze connects to your earbuds over Bluetooth. Make sure they're paired in your phone's Bluetooth settings before getting started.",
            bullets = listOf(
                "Open Bluetooth settings on your phone",
                "Pair your earbuds like normal",
                "Keep earbuds out of the case during setup",
            ),
        ),
        OnboardingPage(
            icon = Icons.Filled.Lock,
            title = "Realme Link Conflict",
            description = "If you have the Realme Link app installed, it can conflict with BudFreeze. Both apps try to control the earbuds at the same time.",
            bullets = listOf(
                "Force-stop Realme Link before using BudFreeze",
                "You can re-open Realme Link later if needed",
                "This prevents connection failures and timeouts",
            ),
        ),
        OnboardingPage(
            icon = Icons.Filled.TouchApp,
            title = "How to Use",
            description = "Tap the left or right bud toggle to lock touch controls. Use the main button to lock or restore both buds at once.",
            bullets = listOf(
                "Tap a bud toggle to lock/unlock individually",
                "Use the main button for both buds",
                "Add the Quick Settings tile for instant access",
            ),
        ),
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.size - 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Top bar with Skip on the right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            if (!isLastPage) {
                TextButton(onClick = {
                    Haptics.click()
                    onFinished()
                }) {
                    Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Pager content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            OnboardingPageContent(pages[page])
        }

        // Bottom section: indicators + button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Page indicators
            Row(
                modifier = Modifier.padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(pages.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (isSelected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                    )
                }
            }

            // Single full-width button
            TouchLockButton(
                text = if (isLastPage) "Get Started" else "Next",
                onClick = {
                    Haptics.click()
                    if (isLastPage) {
                        onFinished()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                height = 52.dp,
            )
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                page.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Title
        Text(
            text = page.title,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Description
        Text(
            text = page.description,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Bullets — left aligned in a card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            page.bullets.forEach { bullet ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = bullet,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val bullets: List<String>,
)
