package com.sarvesh.touchlock

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var budsConnection: BudsConnection

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) detectDevice()
    }

    private var statusMessage by mutableStateOf("Tap a bud to lock it.")
    private var isBusy by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
    private var showFindNearby by mutableStateOf(false)
    private var showSupporter by mutableStateOf(false)
    private var isBeeping by mutableStateOf(false)
    private var beepJob: kotlinx.coroutines.Job? = null
    private lateinit var supporterBilling: SupporterBilling

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        budsConnection = BudsConnection(this)
        TouchLockState.init(this)
        Haptics.init(this)
        supporterBilling = SupporterBilling.get(this)
        supporterBilling.startConnection()

        var contentReady = savedInstanceState != null
        splashScreen.setKeepOnScreenCondition { !contentReady }

        setContent {
            TouchLockAppTheme {
                var showSplash by remember { mutableStateOf(savedInstanceState == null) }

                if (showSplash) {
                    BudsSplash(onFinished = { showSplash = false })
                    LaunchedEffect(Unit) { contentReady = true }
                    return@TouchLockAppTheme
                }

                contentReady = true

                val leftLocked by TouchLockState.leftLocked.collectAsStateWithLifecycle()
                val rightLocked by TouchLockState.rightLocked.collectAsStateWithLifecycle()
                val deviceName by TouchLockState.deviceName.collectAsStateWithLifecycle()
                val battery by TouchLockState.battery.collectAsStateWithLifecycle()
                val connected by TouchLockState.connected.collectAsStateWithLifecycle()
                val isSupporter by supporterBilling.isSupporter.collectAsStateWithLifecycle()

                if (showSupporter) {
                    SupporterSheet(
                        billing = supporterBilling,
                        onDismiss = { showSupporter = false },
                    )
                }

                if (showSettings) {
                    GestureSettingsScreen(
                        initialValues = GestureConfigStore.getGestureValues(this),
                        onSave = { values ->
                            GestureConfigStore.setGestureValues(this, values)
                            showSettings = false
                            statusMessage = "Gestures saved."
                        },
                        onBack = { showSettings = false },
                    )
                } else if (showFindNearby) {
                    val device = budsConnection.findBudsDevice()
                    FindNearbyScreen(
                        deviceMac = device?.address ?: "",
                        deviceName = deviceName,
                        onBack = { showFindNearby = false },
                    )
                } else {
                    TouchLockScreen(
                        statusMessage = statusMessage,
                        leftLocked = leftLocked,
                        rightLocked = rightLocked,
                        isBusy = isBusy,
                        isBeeping = isBeeping,
                        deviceName = deviceName,
                        battery = battery,
                        connected = connected,
                        isSupporter = isSupporter,
                        onLeftTap = { toggleSide(OpoProtocol.SIDE_LEFT) },
                        onRightTap = { toggleSide(OpoProtocol.SIDE_RIGHT) },
                        onLockBoth = { setBoth(true) },
                        onRestoreBoth = { setBoth(false) },
                        onFindClick = { sendCommand(Command.FIND) },
                        onFindStopClick = { sendCommand(Command.FIND_STOP) },
                        onFindNearbyClick = { showFindNearby = true },
                        onSettingsClick = { showSettings = true },
                        onSupporterClick = { showSupporter = true },
                        onConfirmLock = { action -> executeLock(action) },
                    )
                }
            }
        }

        checkPermissionsAndInit()
    }

    override fun onDestroy() {
        supporterBilling.endConnection()
        super.onDestroy()
    }

    private fun checkPermissionsAndInit() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_SCAN)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (needed.isNotEmpty()) bluetoothPermissionLauncher.launch(needed.toTypedArray())
        else detectDevice()
    }

    @SuppressLint("MissingPermission")
    private fun detectDevice() {
        val device = budsConnection.findBudsDevice()
        if (device != null) {
            TouchLockState.setLastDevice(this, device.name)
            lifecycleScope.launch {
                // Query battery to verify the RFCOMM connection actually works.
                // If it fails, the buds are paired but not connectable (Realme Link
                // may be holding the channel, or buds are in the case).
                val batt = budsConnection.queryBattery()
                if (batt != null) {
                    TouchLockState.setConnected(true)
                    TouchLockState.setBattery(batt.first, batt.second, batt.third)
                    updateStatusMessage()
                } else {
                    // Battery query failed — still paired, but can't connect right now.
                    // Mark as connected so the UI is usable, but show a warning.
                    TouchLockState.setConnected(true)
                    statusMessage = "Earbuds paired but not responding. Make sure they're out of the case and Realme Link is force-stopped."
                }
            }
        } else {
            TouchLockState.setConnected(false)
            statusMessage = "No earbuds found. Pair them in Bluetooth settings first."
        }
    }

    private fun updateStatusMessage() {
        val l = TouchLockState.leftLocked.value
        val r = TouchLockState.rightLocked.value
        statusMessage = when {
            l && r -> "Both buds locked."
            l -> "Left bud locked."
            r -> "Right bud locked."
            else -> "Tap a bud to lock it."
        }
    }

    private fun toggleSide(side: Int) {
        if (isBusy) return
        val currentlyLocked = when (side) {
            OpoProtocol.SIDE_LEFT -> TouchLockState.leftLocked.value
            else -> TouchLockState.rightLocked.value
        }
        if (currentlyLocked) {
            sendGestureUpdate(
                newLeftLocked = if (side == OpoProtocol.SIDE_LEFT) false else TouchLockState.leftLocked.value,
                newRightLocked = if (side == OpoProtocol.SIDE_RIGHT) false else TouchLockState.rightLocked.value,
            )
        }
    }

    private fun setBoth(locked: Boolean) {
        if (isBusy) return
        if (!locked) {
            sendGestureUpdate(newLeftLocked = false, newRightLocked = false)
        }
    }

    private fun executeLock(action: LockAction) {
        when (action) {
            LockAction.LEFT -> sendGestureUpdate(
                newLeftLocked = true,
                newRightLocked = TouchLockState.rightLocked.value,
            )
            LockAction.RIGHT -> sendGestureUpdate(
                newLeftLocked = TouchLockState.leftLocked.value,
                newRightLocked = true,
            )
            LockAction.BOTH -> sendGestureUpdate(newLeftLocked = true, newRightLocked = true)
        }
    }

    enum class LockAction { LEFT, RIGHT, BOTH }

    private fun sendGestureUpdate(newLeftLocked: Boolean, newRightLocked: Boolean) {
        if (newLeftLocked && newRightLocked) Haptics.heavy()
        else if (newLeftLocked || newRightLocked) Haptics.medium()
        else Haptics.click()
        lifecycleScope.launch {
            isBusy = true
            statusMessage = "Updating..."

            val gestures = GestureConfigStore.getGestureValues(this@MainActivity)
            val frames = OpoProtocol.buildGestureFrames(newLeftLocked, newRightLocked, gestures)
            val device = budsConnection.sendFrames(frames)

            if (device != null) {
                TouchLockState.setConnected(true)
                TouchLockState.setLeftLocked(this@MainActivity, newLeftLocked)
                TouchLockState.setRightLocked(this@MainActivity, newRightLocked)
                TouchLockState.setLastDevice(this@MainActivity, device)
                TouchLockTileService.requestListening(this@MainActivity)
                updateStatusMessage()
                val batt = budsConnection.queryBattery()
                if (batt != null) TouchLockState.setBattery(batt.first, batt.second, batt.third)
            } else {
                TouchLockState.setConnected(false)
                statusMessage = "Failed to connect. Make sure earbuds are out of the case and Realme Link is force-stopped."
            }
            isBusy = false
        }
    }

    private fun sendCommand(command: Command) {
        // FIND_STOP must work even when isBusy (beeping loop sets isBusy=true)
        if (isBusy && command == Command.FIND) return
        when (command) {
            Command.FIND -> startBeeping()
            Command.FIND_STOP -> stopBeeping()
        }
    }

    private fun startBeeping() {
        beepJob?.cancel()
        beepJob = lifecycleScope.launch {
            isBusy = true
            isBeeping = true
            statusMessage = "Beeping... Tap Stop to silence."
            // Max out prompt volume first so the beep is audible
            android.util.Log.i("Beep", "Setting prompt volume to max...")
            val volFrame = OpoProtocol.buildPromptVolumeFrame(10)
            val volResult = budsConnection.sendFrame(volFrame)
            android.util.Log.i("Beep", "Volume set result: $volResult")
            // Keep sending FIND_DEVICE every ~3s until cancelled or connection fails
            while (isActive && isBeeping) {
                val frame = OpoProtocol.buildFindDeviceFrame()
                val result = budsConnection.sendFrameAndRead(frame, readTimeoutMs = 1000)
                if (result == null) {
                    statusMessage = "Lost connection — stopped beeping."
                    isBeeping = false
                    break
                }
                // Wait before next beep, unless cancelled
                try {
                    kotlinx.coroutines.delay(3000)
                } catch (_: kotlinx.coroutines.CancellationException) {
                    break
                }
            }
            isBusy = false
        }
    }

    private fun stopBeeping() {
        beepJob?.cancel()
        beepJob = null
        // Force-close the socket to interrupt any blocking read in the beep loop.
        // Closing the socket also stops the buds from beeping immediately.
        budsConnection.disconnect()
        isBeeping = false
        isBusy = false
        statusMessage = "Beep stopped."
        // Send FIND_STOP in the background (reconnects silently, no UI blocking)
        lifecycleScope.launch {
            val frame = OpoProtocol.buildFindDeviceStopFrame()
            budsConnection.sendFrameAndRead(frame, readTimeoutMs = 1000)
        }
    }

    private enum class Command { FIND, FIND_STOP }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TouchLockScreen(
    statusMessage: String,
    leftLocked: Boolean,
    rightLocked: Boolean,
    isBusy: Boolean,
    isBeeping: Boolean,
    deviceName: String?,
    battery: TouchLockState.Battery,
    connected: Boolean,
    isSupporter: Boolean,
    onLeftTap: () -> Unit,
    onRightTap: () -> Unit,
    onLockBoth: () -> Unit,
    onRestoreBoth: () -> Unit,
    onFindClick: () -> Unit,
    onFindStopClick: () -> Unit,
    onFindNearbyClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSupporterClick: () -> Unit,
    onConfirmLock: (MainActivity.LockAction) -> Unit,
) {
    val effLeftLocked = connected && leftLocked
    val effRightLocked = connected && rightLocked
    val effBothLocked = effLeftLocked && effRightLocked
    var pendingLock by remember { mutableStateOf<MainActivity.LockAction?>(null) }

    val handleLeftTap: () -> Unit = {
        if (connected && !isBusy) {
            if (effLeftLocked) onLeftTap()
            else pendingLock = MainActivity.LockAction.LEFT
        }
    }
    val handleRightTap: () -> Unit = {
        if (connected && !isBusy) {
            if (effRightLocked) onRightTap()
            else pendingLock = MainActivity.LockAction.RIGHT
        }
    }
    val handleBothClick: () -> Unit = {
        if (connected && !isBusy) {
            if (effBothLocked) onRestoreBoth()
            else pendingLock = MainActivity.LockAction.BOTH
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = onSupporterClick) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = "Support",
                            tint = if (isSupporter) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Device name
            if (deviceName != null) {
                Text(
                    text = deviceName,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp,
                )
            }

            // Connection status chip
            Spacer(modifier = Modifier.height(8.dp))
            ConnectionChip(connected = connected, anyLocked = effLeftLocked || effRightLocked, bothLocked = effBothLocked, leftLocked = effLeftLocked, rightLocked = effRightLocked)

            // Hero earbud visual
            Spacer(modifier = Modifier.height(20.dp))
            EarbudVisual(
                leftPct = battery.left,
                rightPct = battery.right,
                casePct = battery.case,
                leftLocked = effLeftLocked,
                rightLocked = effRightLocked,
                connected = connected,
                onLeftTap = { handleLeftTap() },
                onRightTap = { handleRightTap() },
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (connected) "Tap a bud to toggle lock" else "Connect earbuds to control",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Case battery (only shown if available)
            if (connected && battery.case >= 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Case ${battery.case}%",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }

            // Touch controls card
            Spacer(modifier = Modifier.height(28.dp))
            TouchControlCard(
                leftLocked = effLeftLocked,
                rightLocked = effRightLocked,
                connected = connected,
                onLeftToggle = { handleLeftTap() },
                onRightToggle = { handleRightTap() },
            )

            // Primary action button — Lock/Restore Both
            Spacer(modifier = Modifier.height(20.dp))
            val btnText = if (effBothLocked) "Restore Both" else "Lock Both"
            val btnIcon = if (effBothLocked) Icons.Filled.LockOpen else Icons.Filled.Lock
            val btnColor = if (effBothLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            val btnContainer = if (effBothLocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
            Button(
                onClick = { handleBothClick() },
                enabled = !isBusy && connected,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = btnContainer,
                    contentColor = btnColor,
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                ),
            ) {
                Icon(btnIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(btnText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            // Find Device + Find Nearby — side by side as a row
            Spacer(modifier = Modifier.height(12.dp))
            AnimatedContent(
                targetState = isBeeping,
                transitionSpec = {
                    (fadeIn(tween(200)) togetherWith fadeOut(tween(150)))
                },
                label = "findButton",
            ) { beeping ->
                if (beeping) {
                    Button(
                        onClick = onFindStopClick,
                        enabled = connected,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                        ),
                    ) {
                        Icon(Icons.Filled.SearchOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Stop Beeping", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Find Device (beep)
                        OutlinedButton(
                            onClick = onFindClick,
                            enabled = !isBusy && connected,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Beep", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        // Find Nearby (radar)
                        OutlinedButton(
                            onClick = onFindNearbyClick,
                            enabled = !isBusy && connected,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Filled.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Locate", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // Status / busy indicator
            AnimatedVisibility(visible = isBusy, enter = fadeIn(), exit = fadeOut()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(12.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Animated status message
            Spacer(modifier = Modifier.height(12.dp))
            AnimatedContent(
                targetState = statusMessage,
                transitionSpec = {
                    (fadeIn(tween(200)) togetherWith fadeOut(tween(150)))
                },
                label = "statusMsg",
            ) { msg ->
                Text(
                    text = msg,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    // Confirmation dialog
    pendingLock?.let { action ->
        val title = when (action) {
            MainActivity.LockAction.LEFT -> "Lock left bud?"
            MainActivity.LockAction.RIGHT -> "Lock right bud?"
            MainActivity.LockAction.BOTH -> "Lock both buds?"
        }
        val message = when (action) {
            MainActivity.LockAction.LEFT -> "All touch gestures on the left bud will be disabled."
            MainActivity.LockAction.RIGHT -> "All touch gestures on the right bud will be disabled."
            MainActivity.LockAction.BOTH -> "All touch gestures on both buds will be disabled."
        }
        AlertDialog(
            onDismissRequest = { pendingLock = null },
            title = { Text(title, fontWeight = FontWeight.SemiBold) },
            text = { Text(message, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirmLock(action)
                        pendingLock = null
                    }
                ) {
                    Text("Lock", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingLock = null }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ConnectionChip(
    connected: Boolean,
    anyLocked: Boolean,
    bothLocked: Boolean,
    leftLocked: Boolean,
    rightLocked: Boolean,
) {
    val (text, color, container) = when {
        !connected -> Triple("Disconnected", MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
        bothLocked -> Triple("Both Locked", MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer)
        leftLocked || rightLocked -> Triple("Locked", MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer)
        else -> Triple("Active", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Pulsing dot for active/locked states
            val pulseAlpha by if (connected) {
                rememberInfiniteTransition(label = "dot").animateFloat(
                    initialValue = 0.6f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1500, easing = EaseInOutSine), RepeatMode.Reverse),
                    label = "dotPulse",
                )
            } else {
                remember { mutableStateOf(0.4f) }
            }
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = color.copy(alpha = pulseAlpha),
                modifier = Modifier.size(8.dp),
            ) {}
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = color,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

@Composable
private fun TouchControlCard(
    leftLocked: Boolean,
    rightLocked: Boolean,
    connected: Boolean,
    onLeftToggle: () -> Unit,
    onRightToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Touch Controls",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp,
            )
            Spacer(modifier = Modifier.height(16.dp))
            ControlRow(label = "Left Bud", locked = leftLocked, enabled = connected, onToggle = onLeftToggle)
            Spacer(modifier = Modifier.height(12.dp))
            ControlRow(label = "Right Bud", locked = rightLocked, enabled = connected, onToggle = onRightToggle)
        }
    }
}

@Composable
private fun ControlRow(
    label: String,
    locked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (locked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = label,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (!enabled) "Unavailable" else if (locked) "Locked" else "Unlocked",
                    fontSize = 12.sp,
                    color = if (locked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = locked,
            onCheckedChange = { onToggle() },
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.surface,
                checkedTrackColor = MaterialTheme.colorScheme.error,
                uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}
