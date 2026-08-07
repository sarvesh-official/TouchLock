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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
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
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(80),
        label = "pressScale",
    )

    val shape = RoundedCornerShape(24.dp)

    val (fillColor, textColor, highlightColor) = when (variant) {
        ButtonVariant.PRIMARY -> Triple(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary,
            Color.White.copy(alpha = 0.15f),
        )
        ButtonVariant.DANGER -> Triple(
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onError,
            Color.White.copy(alpha = 0.15f),
        )
    }

    val disabledFill = MaterialTheme.colorScheme.surfaceVariant
    val disabledText = MaterialTheme.colorScheme.onSurfaceVariant
    val effectiveFill = if (enabled) fillColor else disabledFill
    val effectiveText = if (enabled) textColor else disabledText

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .scale(scale)
            .clip(shape)
            .background(effectiveFill)
            .drawWithContent {
                drawContent()
                // Subtle inner top highlight — suggests a curved surface
                if (enabled) {
                    drawLine(
                        color = highlightColor,
                        start = Offset(size.width * 0.15f, 1.5f),
                        end = Offset(size.width * 0.85f, 1.5f),
                        strokeWidth = 1.5f,
                    )
                }
            }
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
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(80),
        label = "pressScale",
    )

    val shape = RoundedCornerShape(20.dp)

    val fillColor = MaterialTheme.colorScheme.surface
    val borderColor = if (enabled) MaterialTheme.colorScheme.outline
                      else MaterialTheme.colorScheme.outlineVariant
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)

    Box(
        modifier = modifier
            .height(height)
            .scale(scale)
            .clip(shape)
            .background(fillColor)
            .border(BorderStroke(1.dp, borderColor), shape)
            .drawWithContent {
                drawContent()
                // Subtle accent-tinted top highlight
                if (enabled) {
                    drawLine(
                        color = highlightColor,
                        start = Offset(size.width * 0.2f, 1f),
                        end = Offset(size.width * 0.8f, 1f),
                        strokeWidth = 1f,
                    )
                }
            }
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

enum class ButtonVariant { PRIMARY, DANGER }
