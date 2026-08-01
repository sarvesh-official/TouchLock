package com.sarvesh.touchlock

import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Find Nearby screen — guides user to lost earbuds using Bluetooth signal + compass.
 *
 * RADAR APPROACH (inspired by SignalHound, blep.fyi, BLE Radar Ultra):
 * - User holds phone to chest and turns slowly
 * - App plots signal strength dots around a radar circle at each compass heading
 * - Stronger signal = dot closer to edge + greener color
 * - Weaker signal = dot closer to center + redder color
 * - The strongest direction gets an "X" marker
 * - A rotating arrow always points toward the X relative to user's current heading
 *
 * Green = close, Red = far.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindNearbyScreen(
    deviceMac: String,
    deviceName: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED &&
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = result.values.all { it }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                )
            )
        }
    }

    val scanner = remember { RssiScanner(context) }

    // Calibration: maxRssi is the RSSI at touching distance (100% proximity).
    // Stored in SharedPreferences so it persists across sessions.
    // Default: -36 (measured for Realme Buds Air 8 on a OnePlus device).
    val prefs = remember { context.getSharedPreferences("find_nearby", android.content.Context.MODE_PRIVATE) }
    var maxRssi by remember { mutableFloatStateOf(prefs.getFloat("maxRssi", -36f)) }
    val minRssi = -90f // far edge = 0%

    // RSSI state
    var currentRssi by remember { mutableStateOf<Int?>(null) }
    var smoothedRssi by remember { mutableStateOf<Float?>(null) }
    val alpha = 0.3f

    // Compass heading (degrees: 0 = North, 90 = East, etc.)
    var currentHeading by remember { mutableFloatStateOf(0f) }

    // Radar samples: list of (heading, rssi) pairs plotted on the radar
    val radarSamples = remember { mutableStateListOf<RadarSample>() }
    var strongestDirection by remember { mutableFloatStateOf(0f) }
    var strongestRssi by remember { mutableIntStateOf(Int.MIN_VALUE) }
    var isSweeping by remember { mutableStateOf(false) }
    var sweepCoveredAngles by remember { mutableStateOf(0) } // how many 15-degree buckets covered

    // Track RSSI via BLE scan (advertising RSSI — NOT power-controlled, correct for direction)
    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        scanner.trackStrongestDevice(intervalMs = 300).collect { device ->
            currentRssi = device.rssi
            smoothedRssi = if (smoothedRssi == null) {
                device.rssi.toFloat()
            } else {
                alpha * device.rssi + (1 - alpha) * smoothedRssi!!
            }

            // Auto-calibrate: if we see a stronger signal than our stored max,
            // update it (user got closer than during manual calibration)
            if (device.rssi > maxRssi) {
                maxRssi = device.rssi.toFloat()
                prefs.edit().putFloat("maxRssi", maxRssi).apply()
            }

            // Record radar sample if sweeping
            if (isSweeping) {
                val heading = currentHeading
                val bucket = (heading / 15f).toInt() * 15f

                // Replace any existing sample at this bucket
                val existingIndex = radarSamples.indexOfFirst { kotlin.math.abs(angleDiff(it.heading, bucket)) < 7.5f }
                val sample = RadarSample(heading = bucket, rssi = device.rssi)
                if (existingIndex >= 0) {
                    if (device.rssi > radarSamples[existingIndex].rssi) {
                        radarSamples[existingIndex] = sample
                    }
                } else {
                    radarSamples.add(sample)
                }

                // Track strongest direction
                if (device.rssi > strongestRssi) {
                    strongestRssi = device.rssi
                    strongestDirection = bucket
                }

                sweepCoveredAngles = radarSamples.size

                // Auto-stop when we've covered most of 360 degrees
                if (radarSamples.size >= 20) {
                    isSweeping = false
                }
            }
        }
    }

    // Compass sensor
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    val rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val orientationAngles = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    val azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                    currentHeading = (azimuth + 360f) % 360f
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (rotationSensor != null) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    // Heartbeat vibration — speeds up and gets stronger as you get closer.
    // Key on the level (0-4) so the effect only restarts when the level CHANGES,
    // not on every RSSI reading. This lets the delay() actually complete.
    val rssiVal = smoothedRssi
    val proximityVal = rssiToProximity(rssiVal, maxRssi, minRssi)
    val heartbeatLevel = when {
        rssiVal == null -> -1
        proximityVal >= 0.90f -> 4    // right here — fast strong heartbeat
        proximityVal >= 0.75f -> 3    // very close
        proximityVal >= 0.55f -> 2    // close
        proximityVal >= 0.30f -> 1    // nearby
        proximityVal >= 0.10f -> 0    // far — slow gentle heartbeat
        else -> -1                     // too far, no vibration
    }

    LaunchedEffect(heartbeatLevel) {
        if (heartbeatLevel < 0) return@LaunchedEffect
        val interval = when (heartbeatLevel) {
            4 -> 400L
            3 -> 600L
            2 -> 900L
            1 -> 1300L
            else -> 1800L
        }
        // Loop: heartbeat, wait, repeat. Only restarts when level changes.
        while (true) {
            Haptics.heartbeat(heartbeatLevel)
            delay(interval)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find Nearby", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (!hasPermission) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Bluetooth permission required.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Device name (small, at top)
            if (deviceName != null) {
                Text(
                    text = deviceName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Radar — takes available space, centered
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                RadarView(
                    rssi = smoothedRssi,
                    currentHeading = currentHeading,
                    samples = radarSamples,
                    strongestDirection = strongestDirection,
                    isSweeping = isSweeping,
                    sweepProgress = radarSamples.size / 24f,
                    maxRssi = maxRssi,
                    minRssi = minRssi,
                    modifier = Modifier.size(280.dp),
                )
            }

            // Proximity label + signal bars (just below radar)
            ProximityLabel(rssi = smoothedRssi, maxRssi = maxRssi, minRssi = minRssi)
            Spacer(modifier = Modifier.height(8.dp))
            SignalBars(rssi = smoothedRssi, maxRssi = maxRssi, minRssi = minRssi)

            Spacer(modifier = Modifier.height(16.dp))

            // Sweep button or progress
            if (currentRssi != null) {
                if (isSweeping) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Turn slowly... ${(radarSamples.size * 100 / 24)}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            radarSamples.clear()
                            strongestRssi = Int.MIN_VALUE
                            strongestDirection = 0f
                            isSweeping = true
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
                    ) {
                        Icon(
                            Icons.Filled.Radar,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (radarSamples.isEmpty()) "Start Sweep" else "Sweep Again",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Instruction (at bottom)
            InstructionText(rssi = smoothedRssi, isSweeping = isSweeping, hasSweep = radarSamples.isNotEmpty(), maxRssi = maxRssi, minRssi = minRssi)

            Spacer(modifier = Modifier.height(8.dp))

            if (currentRssi == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Connecting to earbuds...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

data class RadarSample(val heading: Float, val rssi: Int)

/**
 * The radar view — a circular radar showing:
 * - Dots/blips at each angle where signal was sampled (color = strength)
 * - An "X" marker at the strongest direction
 * - A rotating arrow pointing toward the X (relative to user's current heading)
 * - The whole radar rotates with the compass so directions stay in world coordinates
 */
@Composable
private fun RadarView(
    rssi: Float?,
    currentHeading: Float,
    samples: List<RadarSample>,
    strongestDirection: Float,
    isSweeping: Boolean,
    sweepProgress: Float,
    maxRssi: Float,
    minRssi: Float,
    modifier: Modifier = Modifier,
) {
    val proximity = rssiToProximity(rssi, maxRssi, minRssi)
    val primaryColor = proximityToColor(proximity)

    // Pulse animation for the center circle
    val pulseScale by rememberInfiniteTransition(label = "radar").animateFloat(
        initialValue = 0.88f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when {
                    proximity >= 0.8f -> 400
                    proximity >= 0.5f -> 700
                    proximity >= 0.3f -> 1200
                    else -> 2000
                },
                easing = EaseInOutSine,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    // Radar sweep line animation (rotating line like a sonar)
    val sweepAngle by rememberInfiniteTransition(label = "sweep").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweepAngle",
    )

    // Arrow angle relative to current heading
    val relativeArrowAngle = if (strongestRssiValid(samples)) {
        strongestDirection - currentHeading
    } else 0f
    val animatedArrowAngle by animateFloatAsState(
        targetValue = relativeArrowAngle,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 200f),
        label = "arrowAngle",
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2 - 8.dp.toPx()
            val innerRadius = radius * 0.35f

            // Background circle (dark radar background)
            drawCircle(
                color = Color(0xFF1A1A2E),
                radius = radius,
                center = center,
                style = Fill,
            )

            // Concentric range rings
            for (i in 1..3) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = innerRadius + (radius - innerRadius) * (i / 4f),
                    center = center,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }

            // Cross hairs (vertical + horizontal)
            drawLine(
                color = Color.White.copy(alpha = 0.06f),
                start = Offset(center.x - radius, center.y),
                end = Offset(center.x + radius, center.y),
                strokeWidth = 1.dp.toPx(),
            )
            drawLine(
                color = Color.White.copy(alpha = 0.06f),
                start = Offset(center.x, center.y - radius),
                end = Offset(center.x, center.y + radius),
                strokeWidth = 1.dp.toPx(),
            )

            // Tick marks every 30 degrees
            for (deg in 0 until 360 step 30) {
                val angle = Math.toRadians(deg.toDouble()).toFloat() - (Math.PI / 2).toFloat()
                val start = Offset(
                    center.x + cos(angle) * (radius - 6.dp.toPx()),
                    center.y + sin(angle) * (radius - 6.dp.toPx())
                )
                val end = Offset(
                    center.x + cos(angle) * radius,
                    center.y + sin(angle) * radius
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.15f),
                    start = start,
                    end = end,
                    strokeWidth = 1.dp.toPx(),
                )
            }

            // Radar sweep line (sonar-style rotating line)
            val sweepRad = Math.toRadians((sweepAngle - 90).toDouble()).toFloat()
            drawLine(
                color = primaryColor.copy(alpha = 0.3f),
                start = center,
                end = Offset(
                    center.x + cos(sweepRad) * radius,
                    center.y + sin(sweepRad) * radius,
                ),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            // Sweep trail (fading wedge)
            for (i in 1..20) {
                val trailAngle = sweepAngle - i * 3f
                val trailRad = Math.toRadians((trailAngle - 90).toDouble()).toFloat()
                drawLine(
                    color = primaryColor.copy(alpha = 0.15f * (1f - i / 20f)),
                    start = center,
                    end = Offset(
                        center.x + cos(trailRad) * radius,
                        center.y + sin(trailRad) * radius,
                    ),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            // Plot signal samples as dots around the radar
            // The radar rotates with the compass, so samples are plotted in world coordinates
            // and the whole radar rotates opposite to the heading
            val radarRotation = -currentHeading // rotate radar so North is always at top

            for (sample in samples) {
                // Angle on the radar: sample heading relative to current heading
                val relAngle = sample.heading - currentHeading
                val rad = Math.toRadians((relAngle - 90).toDouble()).toFloat()

                // Distance from center: stronger signal = closer to edge
                val signalProximity = ((sample.rssi + 100f) / 90f).coerceIn(0f, 1f)
                val dotRadius = innerRadius + (radius - innerRadius - 10.dp.toPx()) * signalProximity

                val dotX = center.x + cos(rad) * dotRadius
                val dotY = center.y + sin(rad) * dotRadius

                // Dot color: green = strong, red = weak
                val dotColor = proximityToColor(signalProximity)

                // Glow
                drawCircle(
                    color = dotColor.copy(alpha = 0.2f),
                    radius = 10.dp.toPx(),
                    center = Offset(dotX, dotY),
                    style = Fill,
                )
                // Dot
                drawCircle(
                    color = dotColor,
                    radius = 5.dp.toPx(),
                    center = Offset(dotX, dotY),
                    style = Fill,
                )
            }

            // Draw "X" at the strongest direction
            if (strongestRssiValid(samples)) {
                val relAngle = strongestDirection - currentHeading
                val rad = Math.toRadians((relAngle - 90).toDouble()).toFloat()
                val signalProximity = ((samples.maxOf { it.rssi } + 100f) / 90f).coerceIn(0f, 1f)
                val xRadius = innerRadius + (radius - innerRadius - 10.dp.toPx()) * signalProximity
                val xX = center.x + cos(rad) * xRadius
                val xY = center.y + sin(rad) * xRadius
                val xSize = 10.dp.toPx()

                // X glow
                drawCircle(
                    color = Color(0xFF43A047).copy(alpha = 0.3f),
                    radius = 16.dp.toPx(),
                    center = Offset(xX, xY),
                    style = Fill,
                )

                // X lines
                val xColor = Color(0xFF66BB6A)
                drawLine(
                    color = xColor,
                    start = Offset(xX - xSize, xY - xSize),
                    end = Offset(xX + xSize, xY + xSize),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = xColor,
                    start = Offset(xX - xSize, xY + xSize),
                    end = Offset(xX + xSize, xY - xSize),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            // Outer ring
            drawCircle(
                color = primaryColor.copy(alpha = 0.4f),
                radius = radius,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )

            // Center circle with proximity %
            drawCircle(
                color = primaryColor.copy(alpha = 0.3f),
                radius = innerRadius * pulseScale,
                center = center,
                style = Fill,
            )
            drawCircle(
                color = primaryColor.copy(alpha = 0.6f),
                radius = innerRadius * 0.7f * pulseScale,
                center = center,
                style = Fill,
            )
        }

        // Center text (proximity %)
        if (rssi != null) {
            val pct = (proximity * 100).toInt()
            Text(
                text = "$pct%",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
            )
        } else {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = Color.White.copy(alpha = 0.5f),
            )
        }

        // Direction arrow overlay (points toward X)
        if (strongestRssiValid(samples) && !isSweeping) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(animatedArrowAngle),
            ) {
                val center = Offset(size.width / 2, size.height / 2)
                val arrowLen = size.minDimension / 2 - 20.dp.toPx()
                val arrowHeadY = center.y - arrowLen

                // Arrow shaft
                drawLine(
                    color = Color.White.copy(alpha = 0.9f),
                    start = Offset(center.x, center.y - 30.dp.toPx()),
                    end = Offset(center.x, arrowHeadY + 12.dp.toPx()),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )

                // Arrow head
                val path = Path().apply {
                    moveTo(center.x, arrowHeadY)
                    lineTo(center.x - 10.dp.toPx(), arrowHeadY + 16.dp.toPx())
                    lineTo(center.x + 10.dp.toPx(), arrowHeadY + 16.dp.toPx())
                    close()
                }
                drawPath(path, color = Color.White, style = Fill)
            }
        }
    }
}

/**
 * Simple proximity label — big text, plain language.
 * Green = close, Red = far.
 */
@Composable
private fun ProximityLabel(rssi: Float?, maxRssi: Float, minRssi: Float) {
    val proximity = rssiToProximity(rssi, maxRssi, minRssi)
    val (text, color) = when {
        rssi == null -> "Searching..." to MaterialTheme.colorScheme.onSurfaceVariant
        proximity >= 0.90f -> "RIGHT HERE" to Color(0xFF2E7D32)
        proximity >= 0.75f -> "VERY CLOSE" to Color(0xFF43A047)
        proximity >= 0.55f -> "CLOSE" to Color(0xFF7CB342)
        proximity >= 0.30f -> "NEARBY" to Color(0xFFFDD835)
        else -> "FAR AWAY" to Color(0xFFE53935)
    }

    Text(
        text = text,
        fontSize = 26.sp,
        fontWeight = FontWeight.Black,
        color = color,
        letterSpacing = 2.sp,
    )
}

/**
 * Signal strength bars — like WiFi indicator. Green when full, red when empty.
 */
@Composable
private fun SignalBars(rssi: Float?, maxRssi: Float, minRssi: Float) {
    val proximity = rssiToProximity(rssi, maxRssi, minRssi)
    val color = proximityToColor(proximity)
    val filledBars = when {
        proximity >= 0.85f -> 4
        proximity >= 0.65f -> 3
        proximity >= 0.45f -> 2
        proximity >= 0.25f -> 1
        else -> 0
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (i in 0 until 4) {
            val height = when (i) {
                0 -> 8.dp
                1 -> 12.dp
                2 -> 16.dp
                else -> 20.dp
            }
            val isFilled = i < filledBars
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(height)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isFilled) color else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

@Composable
private fun InstructionText(rssi: Float?, isSweeping: Boolean, hasSweep: Boolean, maxRssi: Float, minRssi: Float) {
    val proximity = rssiToProximity(rssi, maxRssi, minRssi)
    val instruction = when {
        isSweeping -> "Hold phone to your chest. Turn around slowly 360°."
        rssi == null -> "Connecting to earbuds..."
        !hasSweep -> "Tap 'Start Sweep' and turn around to find the direction."
        proximity >= 0.90f -> "Signal is very strong. Check above, below, and behind furniture — the radar only shows horizontal direction."
        proximity >= 0.75f -> "Very close. Also check above and below — on shelves, under cushions, in drawers."
        proximity >= 0.55f -> "Follow the arrow. If it seems wrong, they may be on a different floor or height."
        proximity >= 0.30f -> "Follow the arrow toward the X on the radar."
        else -> "Follow the arrow. The X marks where signal was strongest."
    }

    Text(
        text = instruction,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}

// === Helpers ===

private fun strongestRssiValid(samples: List<RadarSample>): Boolean = samples.isNotEmpty()

private fun angleDiff(a: Float, b: Float): Float {
    val diff = (a - b + 180f) % 360f - 180f
    return if (diff < -180f) diff + 360f else diff
}

private fun rssiToProximity(rssi: Float?, maxRssi: Float, minRssi: Float): Float {
    if (rssi == null) return 0f
    // Calibrated: maxRssi = touching (0m) = 100%, minRssi = far = 0%
    // Power curve gives more sensitivity at close range where it matters
    val t = ((rssi - minRssi) / (maxRssi - minRssi)).coerceIn(0f, 1f)
    return Math.pow(t.toDouble(), 0.6).toFloat()
}

/**
 * Green = close, Red = far.
 */
private fun proximityToColor(proximity: Float): Color {
    return when {
        proximity >= 0.85f -> Color(0xFF2E7D32) // Dark green — right here
        proximity >= 0.65f -> Color(0xFF43A047) // Green — very close
        proximity >= 0.45f -> Color(0xFF7CB342) // Light green — close
        proximity >= 0.25f -> Color(0xFFFDD835) // Yellow — nearby
        else -> Color(0xFFE53935)               // Red — far away
    }
}
