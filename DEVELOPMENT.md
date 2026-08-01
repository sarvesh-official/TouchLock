# Realme Buds Touch Lock — Project Notes

## What this project does
Disables (locks) and restores touch gestures on Realme/OPPO/OnePlus earbuds
from macOS, without using the vendor phone app. Solves the common problem of
accidental touch triggers while sleeping, lying on your side, or adjusting fit.

## Verified working devices
- **Realme Buds Air 8** (MAC 60:55:56:B9:32:70) — `touchlock.swift`
- **OnePlus Nord Buds 3 Pro** (MAC 60:55:56:2A:55:7A) — `touchlock_oneplus.swift`

Both devices share the same OPOv1 protocol, same SDP service layout, and same
command format. Likely also works on: OPPO Enco Air/Air2/Buds2, Realme Buds
T100/T110/T200/T300/Air 5 Pro/Air 6 Pro (all use the same OPOv1 protocol per
Gadgetbridge).

## Verified working approach
- **Transport:** Bluetooth Classic RFCOMM channel 15 (OPOv1 / "Realme Haydn" service)
- **Protocol:** OPOv1 frame format (same as OPPO/Realme headphones in Gadgetbridge)
- **Tool:** `touchlock.swift` / `touchlock_oneplus.swift` (Swift, IOBluetooth)
- **Usage:** `swift touchlock.swift lock` / `swift touchlock.swift restore`

## Key technical findings
- The buds DO speak OPOv1, despite using a BES SoC (not the OPO SoC).
- Channel 15 must be "primed" by opening channel 12 (TOTA) first on macOS.
  Without priming, `openRFCOMMChannelSync` for channel 15 fails with `kIOReturnError`.
- The buds process commands silently — no response is sent back on channel 15.
- The buds must NOT be connected to the Realme Link app on the phone when
  sending commands from the Mac.
- BLE GATT is NOT used for commands. The `01000100` BLE service just echoes writes.
- The serial port `/dev/cu.realmeBudsAir8` (channel 12) is telemetry-only.

## Frame format
```
[0xAA] [len] [0x00] [0x00] [cmdId_lo] [cmdId_hi] [seq] [payloadLen_lo] [payloadLen_hi] [payload]
```
- cmdId and payloadLen are little-endian (matches Gadgetbridge).
- Touch lock uses cmdId 0x0401 (TOUCH_CONFIG_SET) with all gesture values = 0x00.

## Files
- `touchlock.swift` — clean CLI tool (lock/restore/status)
- `docs/PROTOCOL.md` — full protocol documentation
- `toolkit/protocol.py` — Python protocol builder/parser (reference)
- `toolkit/send_ch15.swift` — debug tool with init sequence
- `toolkit/try_channels.swift` — channel scanner
- `toolkit/find_device.swift` — FIND_DEVICE test (buds beep)
- `toolkit/sdp_browse.py` — SDP record browser
- `toolkit/sniffer.py` — serial port sniffer

## Device info
- MAC: 60:55:56:B9:32:70
- Name: realme Buds Air8
- SoC: BES Technoview BES2710IHC
