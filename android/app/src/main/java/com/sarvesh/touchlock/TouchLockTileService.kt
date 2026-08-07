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
 * Quick Settings tile for BudFreeze.
 *
 * Tap to toggle: if either bud is unlocked, lock both. If both are locked, restore both.
 * The tile shows a lock icon when active (touch locked) and an open-lock icon when inactive.
 */
class TouchLockTileService : TileService() {

    companion object {
        private const val TAG = "TouchLockTile"
        private const val PREFS_QS = "qs_prompt"
        private const val KEY_TILE_ADDED = "tile_added"

        fun requestListening(context: android.content.Context) {
            requestListeningState(
                context,
                ComponentName(context, TouchLockTileService::class.java)
            )
        }

        fun isTileAdded(context: android.content.Context): Boolean {
            return context.getSharedPreferences(PREFS_QS, android.content.Context.MODE_PRIVATE)
                .getBoolean(KEY_TILE_ADDED, false)
        }

        fun setTileAdded(context: android.content.Context, added: Boolean) {
            context.getSharedPreferences(PREFS_QS, android.content.Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_TILE_ADDED, added)
                .apply()
        }

        fun shouldShowQsPrompt(context: android.content.Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_QS, android.content.Context.MODE_PRIVATE)
            return !prefs.getBoolean(KEY_QS_PROMPT_SHOWN, false) && !prefs.getBoolean(KEY_TILE_ADDED, false)
        }

        fun markQsPromptShown(context: android.content.Context) {
            context.getSharedPreferences(PREFS_QS, android.content.Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_QS_PROMPT_SHOWN, true)
                .apply()
        }

        private const val KEY_QS_PROMPT_SHOWN = "qs_prompt_shown"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileAdded() {
        super.onTileAdded()
        Log.d(TAG, "Tile added by user")
        setTileAdded(this, true)
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        Log.d(TAG, "Tile removed by user")
        setTileAdded(this, false)
    }

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

        Haptics.init(this)
        Haptics.click()

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
                Haptics.success()
                Log.i(TAG, "Command sent via tile → both locked=$newLocked on $deviceName")
            } else {
                Haptics.error()
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
        tile.label = "BudFreeze"
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
