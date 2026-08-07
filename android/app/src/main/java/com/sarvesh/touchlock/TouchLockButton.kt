package com.sarvesh.touchlock

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun TouchLockButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    icon: ImageVector? = null,
    height: Dp = 52.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = tween(80),
        label = "press",
    )
    val scale = 1f - pressProgress * 0.03f

    val shape = RoundedCornerShape(14.dp)

    val baseColor = when (variant) {
        ButtonVariant.PRIMARY -> MaterialTheme.colorScheme.primary
        ButtonVariant.DANGER -> MaterialTheme.colorScheme.error
        ButtonVariant.SECURE -> MaterialTheme.colorScheme.onSurface
    }
    val onColor = when (variant) {
        ButtonVariant.PRIMARY -> MaterialTheme.colorScheme.onPrimary
        ButtonVariant.DANGER -> MaterialTheme.colorScheme.onError
        ButtonVariant.SECURE -> MaterialTheme.colorScheme.surface
    }

    // Vertical gradient: lighter top → base → darker bottom = curved surface
    val darken = pressProgress * 0.08f
    val topColor = baseColor.lighten(0.10f - darken)
    val midColor = baseColor.darken(darken * 0.5f)
    val bottomColor = baseColor.darken(0.08f + darken)

    val fillGradient = Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to topColor,
            0.5f to midColor,
            1.0f to bottomColor,
        ),
    )

    // Top highlight overlay — white fading from top, clipped to shape
    val density = LocalDensity.current
    val heightPx = with(density) { height.toPx() }
    val highlightGradient = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.18f),
            Color.White.copy(alpha = 0.0f),
        ),
        endY = heightPx * 0.5f,
    )

    // Bottom shadow overlay — black fading from bottom
    val shadowGradient = Brush.verticalGradient(
        colors = listOf(
            Color.Black.copy(alpha = 0.0f),
            Color.Black.copy(alpha = 0.12f),
        ),
        startY = heightPx * 0.5f,
    )

    val disabledFill = MaterialTheme.colorScheme.surfaceVariant
    val disabledText = MaterialTheme.colorScheme.onSurfaceVariant
    val effectiveText = if (enabled) onColor else disabledText

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .scale(scale)
            .clip(shape)
            .background(if (enabled) fillGradient else Brush.verticalGradient(listOf(disabledFill, disabledFill)))
            .background(if (enabled) highlightGradient else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)))
            .background(if (enabled) shadowGradient else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    Haptics.click()
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = effectiveText,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = effectiveText,
            )
        }
    }
}

@Composable
fun TouchLockSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 48.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = tween(80),
        label = "press",
    )
    val scale = 1f - pressProgress * 0.03f

    val shape = RoundedCornerShape(14.dp)

    val surfaceColor = MaterialTheme.colorScheme.surface
    val borderColor = if (enabled) MaterialTheme.colorScheme.outline
                      else MaterialTheme.colorScheme.outlineVariant
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant

    // Subtle vertical gradient on surface
    val darken = pressProgress * 0.05f
    val topColor = surfaceColor.lighten(0.03f)
    val bottomColor = surfaceColor.darken(0.02f + darken)
    val fillGradient = Brush.verticalGradient(
        colors = listOf(topColor, surfaceColor, bottomColor),
    )

    // Top highlight — very subtle for secondary (white cast on light surface is too strong)
    val secDensity = LocalDensity.current
    val secHeightPx = with(secDensity) { height.toPx() }
    val highlightGradient = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.12f),
            Color.White.copy(alpha = 0.0f),
        ),
        endY = secHeightPx * 0.3f,
    )

    Box(
        modifier = modifier
            .height(height)
            .scale(scale)
            .clip(shape)
            .background(fillGradient)
            .background(highlightGradient)
            .border(BorderStroke(1.dp, borderColor), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    Haptics.click()
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
            )
        }
    }
}

private fun Color.lighten(amount: Float): Color = copy(
    red = (red + amount).coerceIn(0f, 1f),
    green = (green + amount).coerceIn(0f, 1f),
    blue = (blue + amount).coerceIn(0f, 1f),
)

private fun Color.darken(amount: Float): Color = copy(
    red = (red - amount).coerceIn(0f, 1f),
    green = (green - amount).coerceIn(0f, 1f),
    blue = (blue - amount).coerceIn(0f, 1f),
)

enum class ButtonVariant { PRIMARY, DANGER, SECURE }
