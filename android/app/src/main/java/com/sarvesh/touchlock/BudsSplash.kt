package com.sarvesh.touchlock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun BudsSplash(onFinished: () -> Unit) {
    val zoom = remember { Animatable(2.5f) }
    val fadeOut = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        delay(150)
        zoom.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 250f))
        delay(400)
        fadeOut.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E0E))
            .graphicsLayer { alpha = fadeOut.value },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = android.graphics.BitmapFactory.decodeResource(
                LocalContext.current.resources,
                R.drawable.splash_logo,
            ).asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .size(140.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    scaleX = zoom.value
                    scaleY = zoom.value
                },
        )
    }
}
