package com.sarvesh.touchlock

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.app.StatusBarManager
import android.os.Build
import android.util.Log
import android.content.Intent
import android.provider.Settings
import java.util.function.Consumer

/**
 * Requests the system to add the BudFreeze Quick Settings tile.
 *
 * On Android 13+ (API 33), uses StatusBarManager.requestAddTileService()
 * which shows a native system dialog — no manual QS panel navigation needed.
 *
 * On older versions, falls back to opening the QS tile settings page.
 */
fun requestAddQsTile(
    context: Context,
    onResult: (TileAddResult) -> Unit = {},
) {
    val componentName = ComponentName(context, TouchLockTileService::class.java)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        try {
            val statusBarManager = context.getSystemService(StatusBarManager::class.java)
            statusBarManager.requestAddTileService(
                componentName,
                "BudFreeze",
                Icon.createWithResource(context, R.drawable.ic_touch_lock),
                context.mainExecutor,
                Consumer { result ->
                    when (result) {
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> {
                            Log.d("BudFreeze", "QS tile added by user")
                            TouchLockTileService.requestListening(context)
                            onResult(TileAddResult.ADDED)
                        }
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> {
                            Log.d("BudFreeze", "QS tile already added")
                            TouchLockTileService.requestListening(context)
                            onResult(TileAddResult.ALREADY_ADDED)
                        }
                        else -> {
                            Log.d("BudFreeze", "QS tile not added (result=$result)")
                            onResult(TileAddResult.CANCELLED)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("BudFreeze", "requestAddTileService failed, falling back", e)
            openQsTileSettings(context)
            onResult(TileAddResult.FALLBACK)
        }
    } else {
        openQsTileSettings(context)
        onResult(TileAddResult.FALLBACK)
    }
}

private fun openQsTileSettings(context: Context) {
    TouchLockTileService.requestListening(context)
    try {
        val intent = Intent("android.settings.QS_TILE_SETTINGS")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        val intent = Intent(Settings.ACTION_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

enum class TileAddResult { ADDED, ALREADY_ADDED, CANCELLED, FALLBACK }
