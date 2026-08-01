# Project: Realme Buds Touch Lock

## One-line summary

Reverse-engineer the BLE protocol used by Realme Buds Air 8 (and the broader BBK earbud ecosystem) to add a "touch lock" feature that disables all touch/gesture controls with one tap, then ship it as (a) a standalone Android app with a Quick Settings tile and (b) an upstream contribution to Gadgetbridge.

## Problem

Realme Buds Air 8 (and the entire Realme / OnePlus / Oppo earbud lineup) does not ship with a "disable all touch controls" feature. Users who sleep with earbuds in, or lie on their side watching video, frequently trigger mistouches — audio pauses, ANC toggles, tracks skip — because the capacitive touch sensor registers pillow/ear contact as a tap.

The official Realme Link app only allows remapping individual gesture slots (double-tap, triple-tap, long-press) to different actions. There is no global "lock all touch" toggle. Setting every slot to "off" manually is tedious and must be reversed to use the buds normally.

## Existing solutions in the market

Touch-lock as a feature is well-established on other brands:

- **Samsung Galaxy Buds** (all models) — "Block touches" toggle in Galaxy Wearable app + a home-screen widget for one-tap lock. The closest existing analog to this project.
- **Soundcore / Anker** — "Control Lock" in the soundcore app; also exposed in Gadgetbridge for Anker models.
- **Bose QuietComfort Ultra Earbuds 2nd Gen** — "Disable touch controls" via app.

The entire BBK ecosystem (Realme, OnePlus, Oppo) lacks this feature despite sharing one BLE protocol (OPOv1). This is the gap the project fills.

## Why this is technically feasible

### Protocol is already partially documented

The BBK earbuds share a BLE protocol called **OPOv1** (Oppo / OnePlus / Realme). Key facts confirmed from public RE work (`AasheeshLikePanner/cracked-oneplus-buds` writeup) and Gadgetbridge source:

- **GATT Service UUID:** `0000079A-D102-11E1-9B23-00025B00A5A5`
- **Write characteristic:** `0100079A-D102-11E1-9B23-00025B00A5A5` (must use `.withoutResponse` write type — `.withResponse` silently fails)
- **Notify characteristic:** `0200079A-D102-11E1-9B23-00025B00A5A5`
- **Secondary service:** `FE2C1234-8366-4814-8EB0-01DE32100BEA` (telemetry/firmware; must also subscribe)
- **Frame format:** `AA LEN 00 00 CAT SUB SEQ payload... checksum`
  - `AA` = header byte
  - `LEN` = length from CAT byte to end
  - `CAT` = category (0x00 System, 0x04 ANC, 0x05 EQ, 0x06 Battery, touch category TBD)
  - `SUB` = sub-command (0x01 Hello, 0x85 Register, 0x04 Set, 0x82 Query)
  - `SEQ` = incrementing sequence number
- **Handshake:** Send HELLO packet → wait 2s → send REGISTER with device token → wait 1.5s → send commands
- **Known token for OnePlus Nord Buds 3 Pro:** `B5 50 A0 69` (likely varies per device family; must be extracted from a btsnoop capture of the Air 8)

### Gadgetbridge already supports 6 Realme models

Gadgetbridge (open-source Android companion, ~Codeberg/Freeyourgadget) supports Realme Buds T100, T110, T200, T300, Air 5 Pro, Air 6 Pro using the same Oppo/Realme protocol implementation. The Air 8 is **not** supported — no PR, no issue, no public attempt. Each supported model was added by a volunteer who owned the device and submitted a PR.

### What is NOT yet known (the actual RE work)

- The touch-control category byte and opcodes for OPOv1 (existing RE only documented ANC, EQ, Battery)
- The "set gesture to off" payload value
- How left vs right earbud is addressed in the payload
- The checksum/CRC algorithm
- The Air 8's specific REGISTER token
- Whether the touch opcodes are identical across all BBK models or vary per device

These will be determined by:
1. Capturing BLE traffic between Realme Link app and Air 8 using Android HCI snoop log + Wireshark
2. Decompiling Realme Link APK with jadx to find the Java code that builds touch-setting commands
3. Cross-referencing captures against the Gadgetbridge Realme/Oppo source code
4. Validating by replaying commands from a custom client

## Architecture

### Layered design (justified by the protocol structure)

```
Layer 4: Device profile          [per-model]    gesture slots, feature flags, register token
Layer 3: Touch-lock feature      [mostly shared] "set all gesture slots to off" sequence
Layer 2: Command opcodes         [mostly shared] category/subcommand/payload for touch settings
Layer 1: Transport (BLE GATT)    [fully shared]  service UUIDs, frame format, write type, handshake
```

The transport layer is generic across all OPOv1 devices. The per-model layer is a small config (register token + list of gesture slots). The middle layers are expected to be shared but require validation per model.

### Standalone app structure

```
realme-buds-touchlock/
├── README.md
├── docs/
│   ├── PROTOCOL.md          # GATT services, characteristics, command opcodes, frame format
│   ├── METHODOLOGY.md       # How the protocol was reverse-engineered (btsnoop, jadx, Wireshark)
│   ├── WRITEUP.md           # Blog-post-style technical narrative for showcase
│   └── captures/            # Redacted .pcap samples, decoded packet tables
├── toolkit/                 # Python RE toolkit (bleak-based)
│   ├── scanner.py           # Discover buds, dump GATT tree
│   ├── sniffer.py           # Passive notify listener
│   ├── command_client.py    # Send decoded commands (touch on/off, etc.)
│   └── decoded_commands.yaml
└── app/                     # Android app (Kotlin + Jetpack Compose)
    ├── OpoV1Connection      # BLE transport + handshake (Layer 1-2)
    ├── DeviceProfile        # Per-model config (Layer 4)
    ├── TouchLockController  # Disable/restore gesture mappings (Layer 3)
    ├── TouchLockTileService # Quick Settings tile (one-tap toggle)
    ├── BleService           # Foreground service holding BLE connection
    └── MainActivity         # Compose UI showing status + toggle
```

### Gadgetbridge contribution

Fork Gadgetbridge, add a RealmeBudsAir8Coordinator reusing the existing Oppo/Realme protocol support classes. Add a "Touch Lock" toggle to the shared Realme support class (benefiting all supported Realme + Oppo models). Submit PR upstream with the protocol documentation.

## Scope and deliverables

| Deliverable | Purpose |
|---|---|
| Standalone Android app with Quick Settings tile | Personal artifact, fully owned, demoable |
| Python RE toolkit (bleak-based) | Proves the protocol understanding independent of Android |
| Protocol documentation (PROTOCOL.md) | The reusable knowledge artifact — first public doc of OPOv1 touch opcodes |
| Methodology writeup (METHODOLOGY.md + WRITEUP.md) | The showcase narrative — how the RE was done, lessons learned |
| Gadgetbridge PR for Air 8 + touch-lock | Open-source contribution, community impact |

## What is explicitly out of scope

- Firmware patching / modification (not needed — the disable-touch command already exists in firmware, just not exposed by the vendor app)
- Cloud services, accounts, telemetry (on-device only)
- Support for non-BBK earbuds (Samsung, Soundcore, etc. use different protocols)
- Audio codec control, EQ, ANC control (touch-lock only; other features are bonus if discovered during RE)

## Risks and unknowns

1. **Touch opcodes may differ per BBK model** — Mitigation: validate on Air 8 first, document the architecture as extensible, be honest in the writeup about how far the generic claim extends.
2. **Air 8 may use a different protocol variant than documented OPOv1** — Mitigation: initial recon with nRF Connect to confirm the `0000079A` service is present. If absent, protocol RE starts from scratch and the project scope grows significantly.
3. **REGISTER token extraction may require rooted device or specific capture conditions** — Mitigation: HCI snoop log works without root on modern Android via bug report; fallback to nRF Connect live inspection.
4. **Gadgetbridge maintainers may not accept the PR** — Mitigation: the standalone app stands on its own as a showcase; the PR is a bonus, not the primary deliverable.

## Showcase framing

> Samsung and Soundcore ship touch-lock. The entire BBK ecosystem (Realme, OnePlus, Oppo) doesn't, despite sharing one BLE protocol. I reverse-engineered the undocumented touch-control opcodes for the OPOv1 protocol, built a one-tap Quick Settings tile that disables all touch gestures on Realme Buds Air 8, and contributed Air 8 support plus a touch-lock toggle to Gadgetbridge — the first public documentation of OPOv1 touch commands and the first touch-lock implementation for any BBK earbud.

## Target audience

Public GitHub repository + technical blog post. Defensive / interoperability framing throughout: this is the user's own device, their own BLE traffic, and the feature is a proven UX pattern from Samsung/Soundcore — not an attack on the vendor.

## Comparable prior work (the projects this will be cited alongside)

- `AasheeshLikePanner/cracked-oneplus-buds` — cracked OPOv1 for OnePlus Nord Buds 3 Pro (ANC only, no touch)
- `JojiiOfficial/GalaxyBuds-rs` — Rust wrapper for Galaxy Buds protocol, includes Un/Lock touchpad
- `sirix12/SoundPeats-Mini-Pro-Hs-Controller` — Kotlin/Compose Android app with touch enable/disable toggle (closest scope analog)
- `Juanipis/motobuds` — macOS app for Motorola Moto Buds, RE'd BES protocol
- `CallMeTak/SoundCoreReversing` — SoundCore Liberty Air 2 Pro RE research log

## Hardware required

- Realme Buds Air 8 (the target device)
- An Android phone (Android 11+, ideally non-rooted to prove the no-root capture workflow) paired with the buds
- A laptop with Wireshark + adb for capture analysis
