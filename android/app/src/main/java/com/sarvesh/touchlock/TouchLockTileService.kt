package com.sarvesh.touchlock

import android.content.ComponentName
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick Settings tile for Touch Lock.
 *
 * Tap to toggle: if either bud is unlocked, lock both. If both are locked, restore both.
 * The tile shows a lock icon when active (touch locked) and an open-lock icon when inactive.
 */
class TouchLockTileService : TileService() {

    companion object {
        private const val TAG = "TouchLockTile"

        fun requestListening(context: android.content.Context) {
            requestListeningState(
                context,
                ComponentName(context, TouchLockTileService::class.java)
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        Log.d(TAG, "onStartListening")
        TouchLockState.init(this)
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, "Tile clicked")

        val leftLocked = TouchLockState.leftLocked.value
        val rightLocked = TouchLockState.rightLocked.value
        // If either is unlocked, lock both. If both locked, restore both.
        val newLocked = !(leftLocked && rightLocked)

        scope.launch {
            updateTileConnecting(newLocked)
        }

        scope.launch {
            val gestures = GestureConfigStore.getGestureValues(this@TouchLockTileService)
            val frames = OpoProtocol.buildGestureFrames(newLocked, newLocked, gestures)

            val connection = BudsConnection(this@TouchLockTileService)
            val deviceName = connection.sendFrames(frames)

            if (deviceName != null) {
                TouchLockState.setBothLocked(this@TouchLockTileService, newLocked)
                TouchLockState.setLastDevice(this@TouchLockTileService, deviceName)
                Log.i(TAG, "Command sent via tile → both locked=$newLocked on $deviceName")
            } else {
                Log.e(TAG, "Failed to send command via tile")
            }

            updateTile()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun updateTile() {
        val tile = qsTile ?: run {
            Log.w(TAG, "qsTile is null")
            return
        }
        val leftLocked = TouchLockState.leftLocked.value
        val rightLocked = TouchLockState.rightLocked.value
        val bothLocked = leftLocked && rightLocked
        val anyLocked = leftLocked || rightLocked

        tile.state = if (bothLocked) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Touch Lock"
        val device = TouchLockState.deviceName.value
        tile.contentDescription = when {
            bothLocked -> "Both buds locked" + (device?.let { " on $it" } ?: "")
            leftLocked -> "Left bud locked" + (device?.let { " on $it" } ?: "")
            rightLocked -> "Right bud locked" + (device?.let { " on $it" } ?: "")
            else -> "Touch active"
        }
        tile.icon = Icon.createWithResource(
            this,
            if (anyLocked) R.drawable.ic_touch_lock else R.drawable.ic_touch_lock_off
        )
        tile.updateTile()
        Log.d(TAG, "Tile updated → L=$leftLocked R=$rightLocked")
    }

    private fun updateTileConnecting(targetState: Boolean) {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_UNAVAILABLE
        tile.label = if (targetState) "Locking…" else "Restoring…"
        tile.icon = Icon.createWithResource(
            this,
            if (targetState) R.drawable.ic_touch_lock else R.drawable.ic_touch_lock_off
        )
        tile.updateTile()
    }
}
