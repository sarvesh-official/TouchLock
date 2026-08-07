package com.sarvesh.touchlock

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    private var bluetoothReceiver: BroadcastReceiver? = null
    private var a2dpProxy: BluetoothProfile? = null

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) detectDevice()
    }

    private var statusMessage by mutableStateOf("Tap a bud to lock it")
    private var isBusy by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
    private var showGestureSettings by mutableStateOf(false)
    private var showPrivacyPolicy by mutableStateOf(false)
    private var showFindNearby by mutableStateOf(false)
    private var findNearbyDevice by mutableStateOf<String?>(null)
    private var showSupporter by mutableStateOf(false)
    private var showQsPrompt by mutableStateOf(false)
    private var isBeeping by mutableStateOf(false)
    private var beepJob: kotlinx.coroutines.Job? = null
    private lateinit var supporterBilling: SupporterBilling

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
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
                    BudsSplash(onFinished = {
                        showSplash = false
                        if (TouchLockTileService.shouldShowQsPrompt(this@MainActivity)) {
                            TouchLockTileService.markQsPromptShown(this@MainActivity)
                            showQsPrompt = true
                        }
                    })
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
                val availableDevices by TouchLockState.availableDevices.collectAsStateWithLifecycle()

                if (showSupporter) {
                    SupporterSheet(
                        billing = supporterBilling,
                        onDismiss = { showSupporter = false },
                    )
                }

                if (showQsPrompt) {
                    LaunchedEffect(Unit) {
                        showQsPrompt = false
                        requestAddQsTile(this@MainActivity) { result ->
                            when (result) {
                                TileAddResult.ADDED, TileAddResult.ALREADY_ADDED -> {
                                    statusMessage = "Quick Settings tile added, swipe down to use it"
                                }
                                TileAddResult.CANCELLED -> {
                                    statusMessage = "You can add the tile later from Settings"
                                }
                                TileAddResult.FALLBACK -> {
                                    statusMessage = "Add the Touch Lock tile from Quick Settings"
                                }
                            }
                        }
                    }
                }

                if (showPrivacyPolicy) {
                    PrivacyPolicyScreen(onBack = { showPrivacyPolicy = false })
                } else if (showGestureSettings) {
                    GestureSettingsScreen(
                        initialValues = GestureConfigStore.getGestureValues(this),
                        onSave = { values ->
                            GestureConfigStore.setGestureValues(this, values)
                            showGestureSettings = false
                            statusMessage = "Gestures saved"
                        },
                        onBack = { showGestureSettings = false },
                    )
                } else if (showSettings) {
                    val tileAdded = remember { TouchLockTileService.isTileAdded(this) }
                    SettingsScreen(
                        onBack = { showSettings = false },
                        onGestureSettingsClick = { showGestureSettings = true },
                        onPrivacyPolicyClick = { showPrivacyPolicy = true },
                        onAddQsTile = {
                            requestAddQsTile(this) { result ->
                                when (result) {
                                    TileAddResult.ADDED, TileAddResult.ALREADY_ADDED -> {
                                        statusMessage = "Quick Settings tile added"
                                    }
                                    else -> {
                                        statusMessage = "Tile not added, you can add it from Quick Settings"
                                    }
                                }
                            }
                        },
                        onSupporterClick = { showSupporter = true },
                        tileAdded = tileAdded,
                    )
                } else if (showFindNearby) {
                    val selectedDevice = availableDevices.find { it.address == findNearbyDevice }
                    FindNearbyScreen(
                        deviceMac = findNearbyDevice ?: "",
                        deviceName = selectedDevice?.name ?: deviceName,
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
                        availableDevices = availableDevices,
                        onSwitchDevice = { addr -> switchDevice(addr) },
                        onLeftTap = { toggleSide(OpoProtocol.SIDE_LEFT) },
                        onRightTap = { toggleSide(OpoProtocol.SIDE_RIGHT) },
                        onLockBoth = { setBoth(true) },
                        onRestoreBoth = { setBoth(false) },
                        onFindClick = { sendCommand(Command.FIND) },
                        onFindStopClick = { sendCommand(Command.FIND_STOP) },
                        onFindNearbyClick = { addr -> findNearbyDevice = addr; showFindNearby = true },
                        onSettingsClick = { showSettings = true },
                        onSupporterClick = { showSupporter = true },
                        onConfirmLock = { action -> executeLock(action) },
                    )
                }
            }
        }

        checkPermissionsAndInit()
    }

    override fun onResume() {
        super.onResume()
        registerBluetoothReceiver()
        // Re-check device state when returning to the app
        if (TouchLockState.selectedDeviceAddress.value != null) {
            detectDevice()
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterBluetoothReceiver()
    }

    override fun onDestroy() {
        try {
            val bluetoothManager = getSystemService(BluetoothManager::class.java)
            bluetoothManager?.adapter?.closeProfileProxy(BluetoothProfile.A2DP, a2dpProxy)
        } catch (_: Exception) {}
        supporterBilling.endConnection()
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    private fun registerBluetoothReceiver() {
        if (bluetoothReceiver != null) return

        bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED,
                    BluetoothDevice.ACTION_ACL_DISCONNECTED,
                    BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
                    BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                        detectDevice()
                    }
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        if (state == BluetoothAdapter.STATE_ON) detectDevice()
                        else if (state == BluetoothAdapter.STATE_OFF) {
                            TouchLockState.setConnected(false)
                            statusMessage = "Bluetooth is off"
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        registerReceiver(bluetoothReceiver, filter)
    }

    private fun unregisterBluetoothReceiver() {
        bluetoothReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        bluetoothReceiver = null
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
        val devices = budsConnection.findBudsDevicesSorted()
        if (devices.isEmpty()) {
            TouchLockState.setConnected(false)
            TouchLockState.setAvailableDevices(emptyList())
            statusMessage = "No earbuds found, pair them in Bluetooth settings first"
            return
        }

        // Populate device list for the dropdown (connected devices first)
        TouchLockState.setAvailableDevices(
            devices.map { TouchLockState.DeviceInfo(it.name ?: "Unknown", it.address) }
        )

        // Pick the device: if user previously selected one, use it.
        // Otherwise, prefer the first connected device.
        val selectedAddr = TouchLockState.selectedDeviceAddress.value
        val device = if (selectedAddr != null) {
            devices.find { it.address == selectedAddr } ?: devices.first()
        } else {
            devices.first()
        }

        TouchLockState.setLastDevice(this, device.name)
        TouchLockState.selectDevice(device.address)

        lifecycleScope.launch {
            // Query battery for THIS specific device only
            val batt = budsConnection.queryBatteryForDevice(device)
            if (batt != null) {
                TouchLockState.setConnected(true)
                TouchLockState.setBattery(batt.first, batt.second, batt.third)
                updateStatusMessage()
            } else {
                TouchLockState.setConnected(false)
                statusMessage = "Can't reach ${device.name ?: "earbuds"}, make sure they're out of the case"
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun switchDevice(address: String) {
        budsConnection.disconnect()
        TouchLockState.setConnected(false)
        TouchLockState.selectDevice(address)
        detectDevice()
    }

    private fun updateStatusMessage() {
        val l = TouchLockState.leftLocked.value
        val r = TouchLockState.rightLocked.value
        statusMessage = when {
            l && r -> "Both buds locked"
            l -> "Left bud locked"
            r -> "Right bud locked"
            else -> "Tap a bud to lock it"
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
                statusMessage = "Failed to connect, make sure earbuds are out of the case and Realme Link is force-stopped"
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
                    statusMessage = "Lost connection, stopped beeping."
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
        statusMessage = "Beep stopped"
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
    availableDevices: List<TouchLockState.DeviceInfo>,
    onSwitchDevice: (String) -> Unit,
    onLeftTap: () -> Unit,
    onRightTap: () -> Unit,
    onLockBoth: () -> Unit,
    onRestoreBoth: () -> Unit,
    onFindClick: () -> Unit,
    onFindStopClick: () -> Unit,
    onFindNearbyClick: (String) -> Unit,
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Device name — tappable dropdown if multiple devices
            if (deviceName != null) {
                var deviceMenuExpanded by remember { mutableStateOf(false) }
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .then(
                                if (availableDevices.size > 1)
                                    Modifier.clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            Haptics.click()
                                            deviceMenuExpanded = true
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                else Modifier.padding(horizontal = 8.dp)
                            ),
                    ) {
                        Text(
                            text = deviceName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (availableDevices.size > 1) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Filled.ArrowDropDown,
                                contentDescription = "Select device",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    if (availableDevices.size > 1) {
                        DropdownMenu(
                            expanded = deviceMenuExpanded,
                            onDismissRequest = { deviceMenuExpanded = false },
                            modifier = Modifier
                                .width(280.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                        ) {
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                availableDevices.forEach { dev ->
                                    val isSelected = dev.name == deviceName
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                else Color.Transparent
                                            )
                                            .clickable {
                                                Haptics.click()
                                                onSwitchDevice(dev.address)
                                                deviceMenuExpanded = false
                                            }
                                            .padding(horizontal = 12.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Filled.Bluetooth,
                                            contentDescription = null,
                                            tint = if (isSelected)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Text(
                                            dev.name,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isSelected)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (isSelected) {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
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
            val btnVariant = ButtonVariant.PRIMARY
            TouchLockButton(
                text = btnText,
                onClick = { handleBothClick() },
                enabled = !isBusy && connected,
                variant = btnVariant,
                icon = btnIcon,
                modifier = Modifier.fillMaxWidth(),
            )

            // Find Device + Find Nearby — compact side-by-side cards
            Spacer(modifier = Modifier.height(12.dp))
            AnimatedContent(
                targetState = isBeeping,
                transitionSpec = {
                    (fadeIn(tween(200)) togetherWith fadeOut(tween(150)))
                },
                label = "findButton",
            ) { beeping ->
                if (beeping) {
                    FindActionCard(
                        icon = Icons.Filled.SearchOff,
                        title = "Stop Beeping",
                        onClick = onFindStopClick,
                        enabled = connected,
                        isDanger = true,
                        isPulsing = true,
                    )
                } else {
                    var locateMenuExpanded by remember { mutableStateOf(false) }
                    val selectedAddr = TouchLockState.selectedDeviceAddress.value
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FindActionCard(
                            icon = Icons.Filled.GraphicEq,
                            title = "Beep",
                            onClick = onFindClick,
                            enabled = !isBusy && connected,
                            modifier = Modifier.weight(1f),
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            FindActionCard(
                                icon = Icons.Filled.Radar,
                                title = "Locate",
                                onClick = { onFindNearbyClick(selectedAddr ?: "") },
                                enabled = !isBusy && connected,
                                trailing = if (availableDevices.size > 1) {
                                    {
                                        Icon(
                                            Icons.Filled.ArrowDropDown,
                                            contentDescription = "Select device",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clickable {
                                                    Haptics.click()
                                                    locateMenuExpanded = true
                                                },
                                        )
                                    }
                                } else null,
                            )
                            if (availableDevices.size > 1) {
                                DropdownMenu(
                                    expanded = locateMenuExpanded,
                                    onDismissRequest = { locateMenuExpanded = false },
                                    modifier = Modifier
                                        .width(280.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                                ) {
                                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                        availableDevices.forEach { dev ->
                                            val isSelected = dev.address == selectedAddr
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                        else Color.Transparent
                                                    )
                                                    .clickable {
                                                        Haptics.click()
                                                        onFindNearbyClick(dev.address)
                                                        locateMenuExpanded = false
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 11.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Icon(
                                                    Icons.Filled.Bluetooth,
                                                    contentDescription = null,
                                                    tint = if (isSelected)
                                                        MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                                Spacer(modifier = Modifier.width(14.dp))
                                                Text(
                                                    dev.name,
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (isSelected)
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
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

        // Floating top bar icons — no background, just overlaid on content
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Heartbeat pulse — mimics a real "lub-dub" heartbeat
            val heartBeat by rememberInfiniteTransition(label = "heart").animateFloat(
                initialValue = 1f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1200
                        // Lub — quick scale up
                        1f at 0 with EaseOut
                        1.25f at 120 with EaseOut
                        1f at 240 with EaseInOut
                        // Dub — slightly smaller second beat
                        1.18f at 360 with EaseOut
                        1f at 480 with EaseInOut
                        // Rest until next cycle
                        1f at 1200
                    },
                ),
                label = "heartBeat",
            )
            IconButton(onClick = {
                Haptics.click()
                onSupporterClick()
            }) {
                Icon(
                    if (isSupporter) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Support",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.scale(heartBeat),
                )
            }
            IconButton(onClick = {
                Haptics.click()
                onSettingsClick()
            }) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
            title = { Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) },
            text = { Text(message, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirmLock(action)
                        pendingLock = null
                    }
                ) {
                    Text("LOCK", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingLock = null }) {
                    Text("CANCEL", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FindActionCard(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    isDanger: Boolean = false,
    isPulsing: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(80),
        label = "cardPress",
    )

    // Subtle pulse for the danger "Stop Beeping" state
    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "pulseAlpha",
    )

    val accentColor = if (isDanger) MaterialTheme.colorScheme.error
                      else MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val borderColor = if (enabled) MaterialTheme.colorScheme.outlineVariant
                      else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val titleColor = if (enabled) MaterialTheme.colorScheme.onSurface
                     else MaterialTheme.colorScheme.onSurfaceVariant

    val dangerBorder = if (isDanger && isPulsing)
        MaterialTheme.colorScheme.error.copy(alpha = 0.3f + pulseAlpha * 0.4f)
    else borderColor

    Column(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
            .border(1.dp, dangerBorder, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    Haptics.click()
                    onClick()
                },
            )
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isDanger && isPulsing)
                        accentColor.copy(alpha = 0.12f + pulseAlpha * 0.1f)
                    else
                        accentColor.copy(alpha = 0.12f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                textAlign = TextAlign.Center,
            )
            if (trailing != null) {
                Spacer(modifier = Modifier.width(2.dp))
                trailing()
            }
        }
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
        bothLocked -> Triple("Both Locked", MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.surfaceVariant)
        leftLocked || rightLocked -> Triple("Locked", MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.surfaceVariant)
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
                tint = if (locked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    color = if (locked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = locked,
            onCheckedChange = { onToggle() },
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.surface,
                checkedTrackColor = MaterialTheme.colorScheme.onSurface,
                uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}
