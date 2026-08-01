# TouchLock

Lock and restore touch gestures on Realme, OnePlus, and OPPO earbuds without the vendor phone app. Solves accidental touch triggers while sleeping, lying on your side, or adjusting fit.

## What it does

Disables (locks) and restores all touch gestures on BBK earbuds with one tap. The entire BBK ecosystem (Realme, OnePlus, OPPO) lacks a global "disable all touch" toggle, even though the firmware supports it. This app exposes that hidden feature.

## Supported devices

- Realme Buds Air 8
- OnePlus Nord Buds 3 Pro

Likely also works on: OPPO Enco Air/Air2/Buds2, Realme Buds T100/T110/T200/T300/Air 5 Pro/Air 6 Pro (all share the same OPOv1 protocol).

## Features

- **Touch lock/unlock** with a single tap, per earbud or both
- **Quick Settings tile** for one-tap toggle from anywhere
- **Find Device** makes earbuds beep so you can locate them by sound
- **Find Nearby** radar screen with signal strength, compass direction, and heartbeat vibration that speeds up as you get closer
- **Gesture settings** to customize individual touch actions
- **Battery display** for each earbud and the case

## How it works

### Transport

Bluetooth Classic RFCOMM channel 15 (OPOv1 / "Realme Haydn" service). On macOS, channel 12 (TOTA) must be opened first to "prime" channel 15.

### Protocol

OPOv1 frame format (same as OPPO/Realme headphones in Gadgetbridge):

```
[0xAA] [len] [0x00] [0x00] [cmdId_lo] [cmdId_hi] [seq] [payloadLen_lo] [payloadLen_hi] [payload]
```

- cmdId and payloadLen are little-endian
- Touch lock uses cmdId 0x0401 (TOUCH_CONFIG_SET) with all gesture values = 0x00

### Find Nearby

Uses BLE advertising RSSI (not GATT connection RSSI, which is power-controlled and gives wrong direction). Body shielding technique: your torso absorbs 10-20 dB of 2.4 GHz signal, so the reading is strongest when facing the earbuds. The app auto-calibrates the RSSI-to-proximity mapping based on your specific earbuds and phone.

## Project structure

```
TouchLock/
  touchlock.swift           # macOS CLI tool (lock/restore/status)
  touchlock_oneplus.swift   # OnePlus variant
  docs/
    PROTOCOL.md             # Full protocol documentation
  toolkit/                  # Reverse engineering tools (Python + Swift)
    protocol.py             # Protocol builder/parser
    scanner.py              # BLE scanner
    sniffer.py              # Serial port sniffer
    sdp_browse.py           # SDP record browser
    find_device.swift       # Find device test
    send_ch15.swift         # Debug tool with init sequence
    try_channels.swift      # Channel scanner
  android/                  # Android app (Kotlin + Jetpack Compose)
    app/src/main/java/com/sarvesh/touchlock/
      MainActivity.kt           # Main screen, navigation
      BudsConnection.kt         # Bluetooth Classic connection
      OpoProtocol.kt            # OPOv1 frame builder
      TouchLockState.kt         # State management
      TouchLockTileService.kt   # Quick Settings tile
      EarbudVisual.kt           # Earbud SVG visual
      Theme.kt                  # Material 3 theme
      Haptics.kt                # Vibration feedback
      BudsSplash.kt             # Splash screen
      GestureConfigStore.kt     # Gesture config storage
      GestureSettingsScreen.kt  # Gesture settings UI
      RssiScanner.kt            # BLE RSSI scanner for Find Nearby
      FindNearbyScreen.kt       # Radar screen with compass + signal dots
```

## Usage

### macOS CLI

```bash
swift touchlock.swift lock
swift touchlock.swift restore
swift touchlock.swift status
```

The earbuds must NOT be connected to the Realme Link app when sending commands.

### Android app

Install the APK, pair your earbuds, and open the app. Tap either earbud to lock/unlock, or use the "Lock Both" button. Add the Quick Settings tile for one-tap access from anywhere.

## Technical findings

- The buds speak OPOv1 despite using a BES SoC (not the OPO SoC)
- The buds process commands silently, no response on channel 15
- BLE GATT is not used for commands, the 01000100 service just echoes writes
- The serial port /dev/cu.realmeBudsAir8 (channel 12) is telemetry-only

## License

MIT
