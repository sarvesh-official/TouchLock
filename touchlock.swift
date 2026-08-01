#!/usr/bin/env swift
// realme-buds-touchlock: Lock or restore touch gestures on Realme Buds Air 8.
//
// Usage:
//   swift touchlock.swift lock      # Disable all touch gestures
//   swift touchlock.swift restore   # Restore default touch gestures
//   swift touchlock.swift status    # Query current touch config (no response expected)
//
// Requirements:
//   - macOS with IOBluetooth framework
//   - Buds must be paired and connected via Bluetooth
//   - Buds must NOT be connected to the Realme Link app on your phone
//
// Protocol:
//   Uses OPOv1 over RFCOMM channel 15 (SDP service "Realme Haydn",
//   UUID 0000079A-D102-11E1-9B23-00025B00A5A5).
//   Channel 12 (TOTA) must be opened first to "prime" the connection,
//   otherwise channel 15 open fails with kIOReturnError.
//   The buds process commands silently — no response is sent back.

import Foundation
import IOBluetooth

let budsAddr = "60:55:56:B9:32:70"

// MARK: - OPOv1 Frame Builder

/// Builds an OPOv1 frame in the Gadgetbridge format:
/// [0xAA] [len] [0x00] [0x00] [cmdId_lo] [cmdId_hi] [seq] [payloadLen LE] [payload]
func buildFrame(cmdId: UInt16, seq: UInt8, payload: [UInt8]) -> [UInt8] {
    let payloadLen = UInt16(payload.count)
    let totalLen = 7 + payload.count
    var frame: [UInt8] = [0xAA, UInt8(totalLen), 0x00, 0x00]
    frame.append(UInt8(cmdId & 0xFF))
    frame.append(UInt8((cmdId >> 8) & 0xFF))
    frame.append(seq)
    frame.append(UInt8(payloadLen & 0xFF))
    frame.append(UInt8((payloadLen >> 8) & 0xFF))
    frame.append(contentsOf: payload)
    return frame
}

/// Builds the TOUCH_CONFIG_SET (0x0401) payload.
/// Format: [count] [side, type_lo, type_hi, value] * count
func buildTouchConfigPayload(entries: [(side: UInt8, type: UInt16, value: UInt8)]) -> [UInt8] {
    var payload: [UInt8] = [UInt8(entries.count)]
    for e in entries {
        payload.append(e.side)
        payload.append(UInt8(e.type & 0xFF))
        payload.append(UInt8((e.type >> 8) & 0xFF))
        payload.append(e.value)
    }
    return payload
}

// Touch config types (from Gadgetbridge TouchConfigType.java)
let TAP_2: UInt16 = 0x0201  // double tap
let TAP_3: UInt16 = 0x0301  // triple tap
let HOLD:  UInt16 = 0x0401  // long press
let UNK_1: UInt16 = 0x0101  // single tap / unknown

// Touch config sides (from Gadgetbridge TouchConfigSide.java)
let LEFT:  UInt8 = 0x01
let RIGHT: UInt8 = 0x02

// Touch config values (from Gadgetbridge TouchConfigValue.java)
let OFF:        UInt8 = 0x00
let PLAY_PAUSE: UInt8 = 0x01
let PREV:       UInt8 = 0x05
let NEXT:       UInt8 = 0x06
let ANC_TOGGLE: UInt8 = 0x03

// All gesture slots on both buds
let ALL_SLOTS: [(side: UInt8, type: UInt16)] = [
    (LEFT,  UNK_1), (LEFT,  TAP_2), (LEFT,  TAP_3), (LEFT,  HOLD),
    (RIGHT, UNK_1), (RIGHT, TAP_2), (RIGHT, TAP_3), (RIGHT, HOLD),
]

// Default gesture mapping (may vary by device — adjust to match your preferences)
let DEFAULT_GESTURES: [(side: UInt8, type: UInt16, value: UInt8)] = [
    (LEFT,  UNK_1, PLAY_PAUSE),
    (LEFT,  TAP_2, NEXT),
    (LEFT,  TAP_3, PREV),
    (LEFT,  HOLD,  ANC_TOGGLE),
    (RIGHT, UNK_1, PLAY_PAUSE),
    (RIGHT, TAP_2, NEXT),
    (RIGHT, TAP_3, PREV),
    (RIGHT, HOLD,  ANC_TOGGLE),
]

// MARK: - RFCOMM Delegate

class RFCommDelegate: NSObject, IOBluetoothRFCOMMChannelDelegate {
    var receivedData: [UInt8] = []

    func rfcommChannelOpenComplete(_ rfcommChannel: IOBluetoothRFCOMMChannel!, status error: IOReturn) {}
    func rfcommChannelClosed(_ rfcommChannel: IOBluetoothRFCOMMChannel!) {}

    func rfcommChannelData(_ rfcommChannel: IOBluetoothRFCOMMChannel!, data dataPointer: UnsafeMutableRawPointer!, length dataLength: Int) {
        let bytes = dataPointer.assumingMemoryBound(to: UInt8.self)
        let data = Array(UnsafeBufferPointer(start: bytes, count: dataLength))
        receivedData.append(contentsOf: data)
    }

    func rfcommChannelWriteComplete(_ rfcommChannel: IOBluetoothRFCOMMChannel!, refcon: UnsafeMutableRawPointer!, status error: IOReturn) {}
}

// MARK: - Main

let args = CommandLine.arguments
let action = args.count > 1 ? args[1].lowercased() : "lock"

guard action == "lock" || action == "restore" || action == "status" else {
    print("Usage: swift touchlock.swift [lock|restore|status]")
    exit(1)
}

guard let device = IOBluetoothDevice(addressString: budsAddr) else {
    print("[!] Could not find Realme Buds Air 8 (\(budsAddr))")
    print("    Make sure the buds are paired and connected via Bluetooth.")
    exit(1)
}

print("[*] Device: \(device.name ?? "?") (\(budsAddr))")
print("[*] Connected: \(device.isConnected()), Paired: \(device.isPaired())")

if !device.isConnected() {
    print("[*] Connecting...")
    _ = device.openConnection()
    Thread.sleep(forTimeInterval: 2)
    if !device.isConnected() {
        print("[!] Could not connect to buds")
        exit(1)
    }
}

// Step 1: Prime the connection by opening channel 12 (TOTA)
print("[*] Priming connection (channel 12)...")
var primeChannel: IOBluetoothRFCOMMChannel?
let primeDelegate = RFCommDelegate()
_ = device.openRFCOMMChannelSync(&primeChannel, withChannelID: 12, delegate: primeDelegate)
Thread.sleep(forTimeInterval: 1.0)

// Step 2: Open channel 15 (OPOv1 / Realme Haydn)
print("[*] Opening command channel (15)...")
let delegate = RFCommDelegate()
var channel: IOBluetoothRFCOMMChannel?
let status = device.openRFCOMMChannelSync(&channel, withChannelID: 15, delegate: delegate)

guard let ch = channel, ch.isOpen() else {
    print("[!] Could not open channel 15 (status: \(status))")
    print("    Make sure the buds are NOT connected to the Realme Link app on your phone.")
    if let c = channel { c.close() }
    if let p = primeChannel { p.close() }
    exit(1)
}

print("[*] Channel 15 open!")

// Step 3: Build and send the command
let frame: [UInt8]
let label: String

if action == "lock" {
    let entries = ALL_SLOTS.map { (side: $0.side, type: $0.type, value: OFF) }
    let payload = buildTouchConfigPayload(entries: entries)
    frame = buildFrame(cmdId: 0x0401, seq: 1, payload: payload)
    label = "TOUCH LOCK (all gestures OFF)"
} else if action == "restore" {
    let payload = buildTouchConfigPayload(entries: DEFAULT_GESTURES)
    frame = buildFrame(cmdId: 0x0401, seq: 1, payload: payload)
    label = "RESTORE (default gestures)"
} else {
    // status: TOUCH_CONFIG_REQ (0x0108)
    frame = buildFrame(cmdId: 0x0108, seq: 1, payload: [0x02, 0x03, 0x01])
    label = "GET TOUCH CONFIG"
}

print("[*] Sending: \(label)")
print("    \(frame.map { String(format: "%02x", $0) }.joined(separator: " "))")

var data = Data(frame)
let wstatus: IOReturn = data.withUnsafeMutableBytes { buf in
    ch.writeSync(buf.baseAddress!, length: UInt16(frame.count))
}

if wstatus == 0 {
    print("[*] Command sent successfully!")
} else {
    print("[!] Write failed (status: \(wstatus))")
}

// Wait briefly for any response (buds usually don't respond)
Thread.sleep(forTimeInterval: 2.0)

if !delegate.receivedData.isEmpty {
    print("[*] Response: \(delegate.receivedData.map { String(format: "%02x", $0) }.joined(separator: " "))")
} else {
    print("[*] No response (normal — buds process commands silently)")
}

// Step 4: Close channels
print("[*] Closing...")
ch.close()
if let p = primeChannel { p.close() }
Thread.sleep(forTimeInterval: 1)

if action == "lock" {
    print("[*] Touch lock engaged! All gestures are now disabled.")
    print("[*] Run 'swift touchlock.swift restore' to re-enable them.")
} else if action == "restore" {
    print("[*] Touch gestures restored to defaults.")
}

print("[*] Done")
