# BudFreeze — Project Rules

## App Overview
BudFreeze (formerly TouchLock) is an Android app that locks/unlocks touch controls on Realme, OnePlus, OPPO Enco, and Nord Buds via Bluetooth RFCOMM.

## Build & Verify
- `cd android && ./gradlew assembleDebug` to build
- APK output: `android/app/build/outputs/apk/debug/app-debug.apk`
- Install: `adb -s <device> install -r app-debug.apk`
- All changes must compile before considering the task complete

## Tech Stack
- Kotlin + Jetpack Compose (Material 3)
- Min SDK 26, Target SDK 35
- Bluetooth Classic RFCOMM (OPO protocol) — not BLE GATT
- Google Play Billing for supporter purchases
- No emojis in UI — use Material icons

## Key Files
- `MainActivity.kt` — main screen, Bluetooth state management, lock/unlock logic
- `BudsConnection.kt` — Bluetooth RFCOMM connection with timeout and device filtering
- `RssiScanner.kt` — BLE scan for RSSI direction finding
- `FindNearbyScreen.kt` — radar-style device locator
- `DeviceScanScreen.kt` — scan all nearby BLE devices (supporter feature)
- `AccentColorStore.kt` — custom accent colors (supporter feature)
- `SupporterSheet.kt` — supporter purchase UI
- `TouchLockTileService.kt` — Quick Settings tile

## Bluetooth Connection
- Uses `isDeviceConnected()` reflection check to skip disconnected devices
- `hasConnectedDevice()` for quick boolean check without battery query
- Socket connect has 3-second timeout via daemon thread (`CONNECT_TIMEOUT_MS`)
- Retry delay between attempts: 200ms (`RETRY_DELAY_MS`)
- Max connect retries: 2 (`MAX_CONNECT_RETRIES`)
- Only tries connected devices — skips paired-but-disconnected ones
- Battery query runs in background, never blocks UI
- Supported device patterns centralized in `OpoProtocol.SUPPORTED_DEVICE_PATTERNS`

## Git
- Never add "Generated with Devin" or "Co-Authored-By: Devin" to commits
- Keep commit messages clean — subject line + body only
