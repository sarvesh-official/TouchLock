package com.sarvesh.touchlock

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Hero earbud visual with battery rings and lock state.
 * Each earbud is tappable to toggle lock state.
 */
@Composable
fun EarbudVisual(
    leftPct: Int,
    rightPct: Int,
    casePct: Int,
    leftLocked: Boolean,
    rightLocked: Boolean,
    connected: Boolean,
    onLeftTap: () -> Unit,
    onRightTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EarbudWithRing(
            pct = leftPct,
            locked = leftLocked,
            connected = connected,
            pulseAlpha = pulseAlpha,
            label = "L",
            onTap = onLeftTap,
            modifier = Modifier.weight(1f),
        )
        EarbudWithRing(
            pct = rightPct,
            locked = rightLocked,
            connected = connected,
            pulseAlpha = pulseAlpha,
            label = "R",
            onTap = onRightTap,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EarbudWithRing(
    pct: Int,
    locked: Boolean,
    connected: Boolean,
    pulseAlpha: Float,
    label: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Smooth scale animation on lock state change
    val scale by animateFloatAsState(
        targetValue = if (locked) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "lockScale",
    )

    // Smooth color transition for the percentage text
    val accentColor = when {
        locked -> MaterialTheme.colorScheme.onSurface
        !connected -> MaterialTheme.colorScheme.outline
        pct < 0 -> MaterialTheme.colorScheme.outline
        pct <= 20 -> Color(0xFFE85252)
        pct <= 50 -> Color(0xFFF0BF40)
        else -> MaterialTheme.colorScheme.primary
    }

    val iconColor = if (!connected) MaterialTheme.colorScheme.outline
        else if (locked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        else MaterialTheme.colorScheme.onSurface

    val outlineColor = MaterialTheme.colorScheme.outline

    Column(
        modifier = modifier
            .clickable(enabled = connected) {
                Haptics.click()
                onTap()
            }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .scale(scale),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val ringRadius = size.minDimension / 2 - 8.dp.toPx()

                // Glow when locked or low battery
                if (locked || (connected && pct in 1..20)) {
                    for (i in 0 until 3) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = (0.08f - i * 0.02f) * pulseAlpha),
                                    Color.Transparent,
                                ),
                                center = center,
                                radius = ringRadius + 16.dp.toPx() + i * 8.dp.toPx(),
                            ),
                            radius = ringRadius + 16.dp.toPx() + i * 8.dp.toPx(),
                            center = center,
                        )
                    }
                }

                // Battery track (dim full ring)
                drawCircle(
                    color = outlineColor.copy(alpha = 0.2f),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = 3.dp.toPx()),
                )

                // Battery arc — filled portion
                if (pct > 0) {
                    val sweepAngle = (pct / 100f) * 360f
                    // Background fill (dim)
                    drawArc(
                        color = accentColor.copy(alpha = 0.15f),
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                        size = Size(ringRadius * 2, ringRadius * 2),
                        style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round),
                    )
                    // Bright arc on top
                    drawArc(
                        color = accentColor.copy(alpha = if (locked) pulseAlpha else 1f),
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                        size = Size(ringRadius * 2, ringRadius * 2),
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round),
                    )
                }

                // Lock containment ring — pulsing outer ring when locked
                if (locked) {
                    drawCircle(
                        color = accentColor.copy(alpha = 0.35f * pulseAlpha),
                        radius = ringRadius + 5.dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }

            // Battery percentage in center
            Text(
                text = when {
                    !connected -> "N/A"
                    pct >= 0 -> "$pct%"
                    else -> "N/A"
                },
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (locked) accentColor else iconColor,
                fontFamily = FontFamily.Monospace,
            )

            // Lock badge — animated in from bottom-right
            androidx.compose.animation.AnimatedVisibility(
                visible = locked,
                enter = androidx.compose.animation.fadeIn(animationSpec = tween(200)) +
                    androidx.compose.animation.scaleIn(
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
                        initialScale = 0.3f,
                    ),
                exit = androidx.compose.animation.fadeOut(animationSpec = tween(150)) +
                    androidx.compose.animation.scaleOut(animationSpec = tween(150), targetScale = 0.3f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // Subtle circle background for the lock icon
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        drawCircle(
                            color = accentColor.copy(alpha = 0.15f),
                            radius = size.minDimension / 2,
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Locked",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
        )
        // Animated lock text
        androidx.compose.animation.AnimatedVisibility(
            visible = locked,
            enter = androidx.compose.animation.fadeIn(tween(200)),
            exit = androidx.compose.animation.fadeOut(tween(150)),
        ) {
            Text(
                text = "LOCKED",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
            )
        }
    }
}
