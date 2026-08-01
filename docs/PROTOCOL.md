# Realme Buds Air 8 — Protocol Documentation

## Status: VERIFIED WORKING
Touch lock and restore have been verified working on Realme Buds Air 8.
The buds DO speak OPOv1 over RFCOMM channel 15, despite using a BES SoC.
The protocol is the same as OPPO/Realme headphones supported by Gadgetbridge.

## CRITICAL FINDING: commands go over Bluetooth Classic, not BLE

**Realme Link sends touch-control commands over Bluetooth Classic (RFCOMM/SPP),
NOT over BLE GATT.** This is why BLE notification capture showed zero packets
when tapping the buds — the command channel is classic Bluetooth.

## VERIFIED Connection Method (macOS)

1. **Pair** the buds with the Mac via System Settings > Bluetooth.
2. **Connect** the buds (they must show as "Connected").
3. **Prime** the connection by opening RFCOMM channel 12 (TOTA service).
   Without this step, opening channel 15 fails with `kIOReturnError`.
4. **Open** RFCOMM channel 15 (SDP service "Realme Haydn",
   UUID `0000079A-D102-11E1-9B23-00025B00A5A5`).
5. **Write** the OPOv1 frame to the channel.
6. The buds process commands **silently** — no response is sent back on channel 15.
   (Gadgetbridge's protocol parser expects responses, but on macOS via IOBluetooth
   we observe none. The commands still take effect immediately.)

**Important:** The buds must NOT be connected to the Realme Link app on your phone
when sending commands from the Mac. The phone app holds a connection that can
prevent channel 15 from opening.

### Transport: Bluetooth Classic RFCOMM

The `Device.J()` and `Device.I()` methods (which actually write command data)
are only implemented in `BRClientDevice` and `BRServiceDevice` (Bluetooth Basic
Rate / Classic). The BLE `GattDevice` class does NOT override these methods —
it only handles setup (battery reading via Heart Rate Service, notification
enabling). The actual command path is:

```
SetCommandManager.s() → IPacketSender.Y1() → TLHeadsetConnectionImp.Y1()
→ BTSDKInitializer.n() → DeviceConnectionImpl.d()
→ MessageTransceiver.d() → TLVDataProcesser.c() → SendHandler → n()
→ m() → DataWriter.a() → DeviceInteractionImpl.d() → device.J()
→ BRClientDevice.J() → BRClientConnection.X() → RFCOMM socket
```

### SPP UUIDs (Bluetooth Classic)

From `BaseBRDevice.java`:
- Data channel 1: `db764ac8-4b08-7f25-aafe-59d03c27bae3`
- Data channel 2: `db764ac8-4b08-7f25-aafe-59d03c27bae4`

These are custom RFCOMM UUIDs, NOT the standard SPP UUID (00001101).
The Air 7 BlueZ dump confirmed `UUID: Serial Port (00001101-...)` is also
advertised, but Realme Link uses the custom UUIDs above.

### What BLE is used for (on the Air 8)

BLE GATT is only used for:
- Battery level reading (Heart Rate Service 0x180D / char 0x2A37)
- Google Fast Pair (service 0xFE2C)
- Find My Device (service 15190001-...)
- Possibly notification subscription setup

The vendor BLE services (66666666, 65786365, 01000100, 86868686, 000008a4)
may be used for other features or may be legacy/unused on the Air 8.

## Implications for the project

1. **The standalone Android app must use Bluetooth Classic RFCOMM, not BLE GATT,
   to send touch-lock commands.** This is a significant architecture change
   from the original plan (which assumed BLE GATT writes).

2. **Bluetooth Classic requires the standard Android Bluetooth permissions
   (CONNECT, not just BLE scan/connect).** The app needs
   `android.permission.BLUETOOTH_CONNECT` for RFCOMM.

3. **The bleak-based Python toolkit cannot easily send commands** — bleak is
   BLE-only. We'd need PyBlueZ or a socket-based approach for Bluetooth Classic
   on macOS/Linux. This makes the Python RE toolkit harder.

4. **The HCI snoop log capture IS still valid** — it captures both BLE and
   Classic Bluetooth traffic. The btsnoop log from the phone would contain
   the RFCOMM writes. We just couldn't pull it without root.

5. **The Gadgetbridge contribution is more complex** — Gadgetbridge's existing
   Realme/Oppo support uses BLE GATT (OPOv1). Adding Air 8 support would
   require a new Bluetooth Classic transport layer.

## GATT services on Air 8 (from bleak scan)

| Service | Likely role |
|---|---|
| 0000fe2c-... (Google Fast Pair) | Fast Pair spec — irrelevant |
| 66666666-... / char 77777777-... | BES vendor — bidirectional, also on Air 7 |
| 65786365-6c70-6f69-6e74-2e636f820000 ("excelpoint.co") | BES distributor — 2 write + 2 notify |
| 01000100-0000-1000-8000-009078563412 | OPOv1-like layout (write + notify) |
| 86868686-... / char 97979797-... | BES vendor — bidirectional |
| 000008a4-... | Short UUID vendor service |
| 15190001-... | Google Fast Pair Find My Device — irrelevant |

All vendor services except 66666666 and 000008a4 require encrypted (bonded) BLE connection.

## Touch-control protocol (from APK decompilation)

### Frame format: OPPOv1Wrapper
- Header byte: 0xAA (decimal -86)
- Variable-length encoding (LEB128-style) for length field
- Multi-frame support for payloads > MTU
- Source: com/realme/iot/headset/tl/internal/message/wrapper/OPPOv1Wrapper.java

### Set Key Function command (TOUCH LOCK target)

**Command ID:** `Protocol.Z0 = 1025 = 0x0401`
**Response ID:** `Protocol.a1 = 33793 = 0x8401`

**Payload format:**
```
[1 byte: count] [4 bytes per KeyFunctionInfo entry]
```

Each KeyFunctionInfo entry is 4 bytes:
```
byte 0: device_type   (1=left bud, 2=right bud, 4=case)
byte 1: button        (1=touch sensor, 4=physical button)
byte 2: action        (1=single tap, 2=double tap, 3=triple tap,
                       4=long press, 5=hold, 6=???)
byte 3: function      (0=OFF/DISABLED, 1=play/pause, 6=next track,
                       5=prev track, 3=ANC toggle, 4=voice assistant,
                       8=volume+, 13=volume-, 11=EQ, 12=game mode,
                       17=???, 26/27/31=other)
```

### Function value mapping (from ParamsConverter.A())

| UI setting value | Protocol function byte | Meaning |
|---|---|---|
| (any other / default) | **0** | **OFF / DISABLED** ← touch lock! |
| 1 | 1 | Play/Pause |
| 2 | 6 | Next track |
| 3 | 5 | Previous track |
| 4 | 3 or 4 | ANC toggle (depends on FeatureOption) |
| 5 | 8 | Volume up |
| 6 | 13 | Volume down |
| 7 | 17 | (unknown) |
| 8 | 11 | EQ cycle |
| 9 | 12 | Game mode toggle |
| 26 | 26 | (unknown — possibly AI feature) |
| 27 | 27 | (unknown) |
| 31 | 31 | (unknown) |

### Action/gesture mapping (from ParamsConverter.r())

| settingType | settingKey | device_type | button | action | Meaning |
|---|---|---|---|---|---|
| 4 | 1 | 1 | 4 | 1 | Left bud physical button single press |
| 4 | 2 | 1 | 4 | 2 | Left bud physical button double press |
| 4 | 3 | 1 | 4 | 3 | Left bud physical button triple press |
| 4 | 4 | 1 | 4 | 4 | Left bud physical button long press |
| 5 | 1 | 1 | 1 | 1 | Left bud touch single tap |
| 5 | 2 | 1 | 1 | 2 | Left bud touch double tap |
| 5 | 3 | 1 | 1 | 3 | Left bud touch triple tap |
| 5 | 4 | 1 | 1 | 4 | Left bud touch long press |
| 5 | 6 | 1 | 1 | 6 | Left bud touch (6=???) |
| 6 | 2 | 1 | 1 | 2 | (variant) double tap |
| 6 | 3 | 1 | 1 | 3 | (variant) triple tap |
| 6 | 4 | 1 | 1 | 4 | (variant) long press |
| 6 | 5 | 1 | 1 | 5 | (variant) hold |
| 7 | 2 | 2 | 1 | 2 | Right bud touch double tap |
| 7 | 3 | 2 | 1 | 3 | Right bud touch triple tap |
| 7 | 4 | 2 | 1 | 4 | Right bud touch long press |
| 8 | 4 | 4 | 1 | 4 | Case touch long press |

## Touch Lock implementation

To disable ALL touch gestures (touch lock), send command 0x0401 (TOUCH_CONFIG_SET)
with a payload containing one entry per gesture slot, all with value=0 (OFF):

### Payload format (matches Gadgetbridge `encodeTouchConfigSet`)

```
[1 byte: count]
[4 bytes per entry]: [side] [type_lo] [type_hi] [value]
```

- **side**: 0x01 = LEFT, 0x02 = RIGHT, 0x04 = BOTH (from `TouchConfigSide.java`)
- **type**: 16-bit little-endian (from `TouchConfigType.java`):
  - 0x0101 = UNK_1 (single tap)
  - 0x0201 = TAP_2 (double tap)
  - 0x0301 = TAP_3 (triple tap)
  - 0x0401 = HOLD (long press)
- **value**: 0x00 = OFF, 0x01 = PLAY_PAUSE, 0x05 = PREV, 0x06 = NEXT,
  0x03 = ANC_TOGGLE (from `TouchConfigValue.java`)

### Touch lock payload (all gestures OFF):
```
08              <- count = 8 entries
01 01 01 00     <- LEFT,  single tap, OFF
01 02 01 00     <- LEFT,  double tap, OFF
01 03 01 00     <- LEFT,  triple tap, OFF
01 04 01 00     <- LEFT,  long press, OFF
02 01 01 00     <- RIGHT, single tap, OFF
02 02 01 00     <- RIGHT, double tap, OFF
02 03 01 00     <- RIGHT, triple tap, OFF
02 04 01 00     <- RIGHT, long press, OFF
```

### Restore payload (default gestures):
```
08              <- count = 8 entries
01 01 01 01     <- LEFT,  single tap, PLAY_PAUSE
01 02 01 06     <- LEFT,  double tap, NEXT
01 03 01 05     <- LEFT,  triple tap, PREV
01 04 01 03     <- LEFT,  long press, ANC_TOGGLE
02 01 01 01     <- RIGHT, single tap, PLAY_PAUSE
02 02 01 06     <- RIGHT, double tap, NEXT
02 03 01 05     <- RIGHT, triple tap, PREV
02 04 01 03     <- RIGHT, long press, ANC_TOGGLE
```

This payload is wrapped in the OPOv1 frame format (0xAA header + length +
command ID 0x0401 + payload) and written to RFCOMM channel 15.

## No handshake required

On RFCOMM connect, `TLHeadsetConnectionImp.q6()` calls `this.m.u0(str)` which
polls device info using commands 256 (0x100), 257 (0x101), 258 (0x102),
259 (0x103), 260 (0x104 = protocol version "0.0.1"). There is NO HELLO,
NO REGISTER, NO device token, NO authentication. The buds accept commands
as soon as the RFCOMM socket is open.

## Command IDs (from Gadgetbridge `OppoCommand.java`)

| Command | ID | Description |
|---|---|---|
| BATTERY_REQ | 0x0106 | Request battery levels |
| BATTERY_RET | 0x8106 | Battery response |
| SUBSCRIPTION_SET | 0x0205 | Subscribe to notifications |
| SUBSCRIPTION_ACK | 0x8205 | Subscription ack |
| SUBSCRIPTION_RET | 0x0204 | Subscription notification |
| FIRMWARE_GET | 0x0105 | Request firmware version |
| FIRMWARE_RET | 0x8105 | Firmware response |
| TOUCH_CONFIG_REQ | 0x0108 | Request touch config |
| **TOUCH_CONFIG_SET** | **0x0401** | **Set touch config (touch lock!)** |
| TOUCH_CONFIG_RET | 0x8108 | Touch config response |
| TOUCH_CONFIG_ACK | 0x8401 | Touch config ack |
| FIND_DEVICE_REQ | 0x0400 | Find device (beeps) |
| FIND_DEVICE_ACK | 0x8400 | Find device ack |
| MISC_CONFIG_SET | 0x0403 | Set misc config (LDAC, game mode, multipoint) |
| MISC_CONFIG_REQ | 0x010d | Request misc config |
| MISC_CONFIG_ACK | 0x8403 | Misc config ack |
| MISC_CONFIG_RET | 0x810d | Misc config response |
| ANC_CONFIG_SET | 0x0404 | Set ANC mode |
| ANC_CONFIG_REQ | 0x010c | Request ANC config |
| ANC_CONFIG_ACK | 0x8404 | ANC config ack |
| ANC_CONFIG_RET | 0x810c | ANC config response |

## SDP services on Air 8 (RFCOMM channels)

| Channel | Service Name | UUID | Role |
|---|---|---|---|
| 1 | (unnamed) | 0x111E / 0x1203 | Handsfree / Phone book |
| 12 | TOTA | (TOTA UUID) | Telemetry — used to prime channel 15 |
| 13 | BESOTA | 66666666-... | BES OTA / data |
| **15** | **Realme Haydn** | **0000079A-D102-11E1-9B23-00025B00A5A5** | **OPOv1 command channel** |
| 17 | RFCOMM COM | df21fe2c-... | Unknown protocol (responds but not OPOv1) |
| 24 | UUID128 | 00005555-... | Unknown |
| 29 | WATCH | 99999999-... | Unknown |

## Complete frame encoding (VERIFIED — matches Gadgetbridge)

### Frame format (Gadgetbridge `OppoHeadphonesProtocol.encodeMessage`)

```
[0xAA]              ← SOF (start of frame)
[len]               ← total length minus 2 (single byte for small frames)
[0x00]              ← zero byte (or 0x04 on some Realme devices)
[0x00]              ← zero byte
[cmdId_lo]          ← command ID low byte (little-endian)
[cmdId_hi]          ← command ID high byte
[seq]               ← sequence number (incrementing)
[payloadLen_lo]     ← payload length low byte (little-endian)
[payloadLen_hi]     ← payload length high byte
[payload...]        ← payload bytes
```

**Note:** The command ID is stored as `(cmdId & 0xFF)` then `(cmdId >> 8)`,
which is little-endian. This matches Gadgetbridge's `ByteBuffer.putShort()`
with `ByteOrder.LITTLE_ENDIAN`.

### Touch-lock command: exact wire bytes (VERIFIED WORKING)

For command 0x0401 (TOUCH_CONFIG_SET) with 8 gesture slots all set to OFF:

**Full frame (42 bytes) — what goes on the wire:**
```
AA 28 00 00 01 04 01 21 00 08 01 01 01 00 01 01 02 00 01 01 03 00 01 01 04 00 02 01 01 00 02 01 02 00 02 01 03 00 02 01 04 00
```

Breakdown:
```
AA          ← SOF
28          ← length = 40 (total frame - 2)
00 00       ← two zero bytes
01 04       ← commandId 0x0401 (little-endian: lo=0x01, hi=0x04)
01          ← sequence number
21 00       ← payload length 33 (little-endian)
08          ← count = 8 entries
01 01 01 00 ← left  single  OFF
01 01 02 00 ← left  double  OFF
01 01 03 00 ← left  triple  OFF
01 01 04 00 ← left  long    OFF
02 01 01 00 ← right single  OFF
02 01 02 00 ← right double  OFF
02 01 03 00 ← right triple  OFF
02 01 04 00 ← right long    OFF
```

### Response

Response command ID: `0x8401` (TOUCH_CONFIG_ACK)
Response payload: 1 byte status (0 = success, nonzero = error)
The response is wrapped in the same OPOv1 frame format.
**Note:** On macOS via IOBluetooth, no response is observed on channel 15,
but the command takes effect immediately. The buds process commands silently.

## How to send (standalone implementation)

### macOS (verified working — see `touchlock.swift`)

1. **Pair** the buds with the Mac via System Settings > Bluetooth.
2. **Connect** the buds (must show as "Connected").
3. **Prime** by opening RFCOMM channel 12 (TOTA) via `IOBluetoothDevice.openRFCOMMChannelSync`.
4. **Open** RFCOMM channel 15 via `IOBluetoothDevice.openRFCOMMChannelSync`.
5. **Write** the 42-byte frame via `IOBluetoothRFCOMMChannel.writeSync`.
6. **Close** channels.

### Android

Use `BluetoothDevice.createRfcommSocketToServiceRecord(UUID)` with the
OPOv1 UUID `0000079A-D102-11E1-9B23-00025B00A5A5`. The Android Bluetooth
stack handles SDP lookup and channel selection automatically.
No priming step is needed on Android (the priming workaround is macOS-specific).

### Linux

Use BlueZ RFCOMM socket. The `rfcomm` command or `BluetoothSocket` API
can connect to the OPOv1 UUID.

## Source files (decompiled APK)

- Frame format: com/realme/iot/headset/tl/internal/message/wrapper/OPPOv1Wrapper.java
- Command IDs: com/realme/iot/headset/tl/protocol/packet/Protocol.java
- Set key function: com/realme/iot/headset/tl/protocol/commands/SetCommandManager.java (method s())
- KeyFunctionInfo struct: com/realme/iot/headset/tl/protocol/commands/KeyFunctionInfo.java
- Function value mapping: com/realme/iot/headset/tl/ParamsConverter.java (method A())
- Gesture slot mapping: com/realme/iot/headset/tl/ParamsConverter.java (method r())
