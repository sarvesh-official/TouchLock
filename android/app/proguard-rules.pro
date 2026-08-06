# ─── Compose ────────────────────────────────────────────────────────────────
# Compose is generally safe to obfuscate, but keep the runtime
-keep class androidx.compose.runtime.** { *; }

# ─── Kotlin Coroutines ──────────────────────────────────────────────────────
-keepclassmembers class kotlinx.coroutines.** { *; }

# ─── Bluetooth ──────────────────────────────────────────────────────────────
# Keep Bluetooth service classes (referenced by name in manifest)
-keep class com.sarvesh.touchlock.TouchLockTileService { *; }
-keep class com.sarvesh.touchlock.MainActivity { *; }

# ─── Play Billing (when added) ──────────────────────────────────────────────
-keep class com.android.vending.billing.** { *; }

# ─── Keep enum values ───────────────────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
