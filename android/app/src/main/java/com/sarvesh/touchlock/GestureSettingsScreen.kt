package com.sarvesh.touchlock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureSettingsScreen(
    initialValues: List<Int>,
    onSave: (List<Int>) -> Unit,
    onBack: () -> Unit,
) {
    val gestures = remember { mutableStateListOf<Int>().apply { addAll(initialValues) } }
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Touch Controls", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
        ) {
            Text(
                text = "Pick what each gesture does. Changes apply when you tap Restore.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 20.dp),
            )

            // Left bud section
            SectionHeader("LEFT BUD")
            Spacer(modifier = Modifier.height(10.dp))
            GestureSlot(
                icon = Icons.Filled.Fingerprint,
                gestureName = "Single Tap",
                selectedCode = gestures[0],
                onSelected = { code -> gestures[0] = code },
            )
            GestureSlot(
                icon = Icons.Filled.Repeat,
                gestureName = "Double Tap",
                selectedCode = gestures[1],
                onSelected = { code -> gestures[1] = code },
            )
            GestureSlot(
                icon = Icons.Filled.MoreHoriz,
                gestureName = "Triple Tap",
                selectedCode = gestures[2],
                onSelected = { code -> gestures[2] = code },
            )
            GestureSlot(
                icon = Icons.Filled.PanTool,
                gestureName = "Long Press",
                selectedCode = gestures[3],
                onSelected = { code -> gestures[3] = code },
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Right bud section
            SectionHeader("RIGHT BUD")
            Spacer(modifier = Modifier.height(10.dp))
            GestureSlot(
                icon = Icons.Filled.Fingerprint,
                gestureName = "Single Tap",
                selectedCode = gestures[4],
                onSelected = { code -> gestures[4] = code },
            )
            GestureSlot(
                icon = Icons.Filled.Repeat,
                gestureName = "Double Tap",
                selectedCode = gestures[5],
                onSelected = { code -> gestures[5] = code },
            )
            GestureSlot(
                icon = Icons.Filled.MoreHoriz,
                gestureName = "Triple Tap",
                selectedCode = gestures[6],
                onSelected = { code -> gestures[6] = code },
            )
            GestureSlot(
                icon = Icons.Filled.PanTool,
                gestureName = "Long Press",
                selectedCode = gestures[7],
                onSelected = { code -> gestures[7] = code },
            )

            Spacer(modifier = Modifier.height(24.dp))

            TouchLockButton(
                text = "Save Gestures",
                onClick = { onSave(gestures.toList()) },
                icon = Icons.Filled.Check,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GestureSlot(
    icon: ImageVector,
    gestureName: String,
    selectedCode: Int,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = OpoProtocol.GESTURE_OPTIONS.find { it.code == selectedCode }
    val selectedLabel = selectedOption?.label ?: "Off"
    val selectedIcon = gestureOptionIcon(selectedCode)

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = gestureName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                selectedIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = selectedLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            OpoProtocol.GESTURE_OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                gestureOptionIcon(option.code),
                                contentDescription = null,
                                tint = if (option.code == selectedCode)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                option.label,
                                fontSize = 14.sp,
                                fontWeight = if (option.code == selectedCode)
                                    FontWeight.Bold else FontWeight.Normal,
                                color = if (option.code == selectedCode)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            if (option.code == selectedCode) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    },
                    onClick = {
                        onSelected(option.code)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun gestureOptionIcon(code: Int): ImageVector = when (code) {
    OpoProtocol.GESTURE_OFF -> Icons.Filled.Block
    OpoProtocol.GESTURE_PLAY_PAUSE -> Icons.Filled.PlayArrow
    OpoProtocol.GESTURE_NEXT -> Icons.Filled.SkipNext
    OpoProtocol.GESTURE_PREVIOUS -> Icons.Filled.SkipPrevious
    OpoProtocol.GESTURE_NOISE_CONTROL -> Icons.Filled.GraphicEq
    OpoProtocol.GESTURE_VOICE_ASSISTANT -> Icons.Filled.Mic
    OpoProtocol.GESTURE_VOLUME_UP -> Icons.Filled.VolumeUp
    OpoProtocol.GESTURE_VOLUME_DOWN -> Icons.Filled.VolumeDown
    OpoProtocol.GESTURE_GAME_MODE -> Icons.Filled.SportsEsports
    else -> Icons.Filled.Block
}
