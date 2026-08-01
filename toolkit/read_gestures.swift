#!/usr/bin/env swift
// read_gestures.swift: Query current touch config from BBK earbuds.
//
// Sends TOUCH_CONFIG_REQ (0x0108) and listens for a response on both
// channel 15 and channel 12 for up to 10 seconds.
//
// Usage: swift read_gestures.swift [mac_address]

import Foundation
import IOBluetooth

let budsAddr = CommandLine.arguments.count > 1
    ? CommandLine.arguments[1]
    : "60:55:56:B9:32:70"

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

class RFCommDelegate: NSObject, IOBluetoothRFCOMMChannelDelegate {
    var receivedData: [UInt8] = []
    var channelName: String

    init(name: String) {
        self.channelName = name
    }

    func rfcommChannelOpenComplete(_ rfcommChannel: IOBluetoothRFCOMMChannel!, status error: IOReturn) {
        print("[\(channelName)] Channel opened (status: \(error))")
    }

    func rfcommChannelClosed(_ rfcommChannel: IOBluetoothRFCOMMChannel!) {
        print("[\(channelName)] Channel closed")
    }

    func rfcommChannelData(_ rfcommChannel: IOBluetoothRFCOMMChannel!, data dataPointer: UnsafeMutableRawPointer!, length dataLength: Int) {
        let bytes = dataPointer.assumingMemoryBound(to: UInt8.self)
        let data = Array(UnsafeBufferPointer(start: bytes, count: dataLength))
        receivedData.append(contentsOf: data)
        print("[\(channelName)] Received \(dataLength) bytes: \(data.map { String(format: "%02x", $0) }.joined(separator: " "))")
    }

    func rfcommChannelWriteComplete(_ rfcommChannel: IOBluetoothRFCOMMChannel!, refcon: UnsafeMutableRawPointer!, status error: IOReturn) {
        print("[\(channelName)] Write complete (status: \(error))")
    }
}

guard let device = IOBluetoothDevice(addressString: budsAddr) else {
    print("[!] Could not find device \(budsAddr)")
    exit(1)
}

print("[*] Device: \(device.name ?? "?") (\(budsAddr))")
print("[*] Connected: \(device.isConnected()), Paired: \(device.isPaired())")

if !device.isConnected() {
    print("[*] Connecting...")
    _ = device.openConnection()
    Thread.sleep(forTimeInterval: 2)
    if !device.isConnected() {
        print("[!] Could not connect")
        exit(1)
    }
}

// Open channel 12 (TOTA) — priming + listen for responses
print("[*] Opening channel 12 (TOTA)...")
var ch12: IOBluetoothRFCOMMChannel?
let del12 = RFCommDelegate(name: "ch12")
_ = device.openRFCOMMChannelSync(&ch12, withChannelID: 12, delegate: del12)
Thread.sleep(forTimeInterval: 1.0)

// Open channel 15 (OPOv1)
print("[*] Opening channel 15 (OPOv1)...")
var ch15: IOBluetoothRFCOMMChannel?
let del15 = RFCommDelegate(name: "ch15")
let status = device.openRFCOMMChannelSync(&ch15, withChannelID: 15, delegate: del15)

guard let ch = ch15, ch.isOpen() else {
    print("[!] Could not open channel 15 (status: \(status))")
    if let c = ch12 { c.close() }
    exit(1)
}

print("[*] Channel 15 open!")

// Send TOUCH_CONFIG_REQ (0x0108)
// Try different payloads that might trigger a response
let payloads: [[UInt8]] = [
    [0x02, 0x03, 0x01],  // original payload from touchlock.swift
    [0x01],               // simple request
    [],                   // empty payload
]

for (i, payload) in payloads.enumerated() {
    let frame = buildFrame(cmdId: 0x0108, seq: UInt8(i + 1), payload: payload)
    print("[*] Sending TOUCH_CONFIG_REQ attempt \(i + 1): \(frame.map { String(format: "%02x", $0) }.joined(separator: " "))")

    var data = Data(frame)
    let wstatus: IOReturn = data.withUnsafeMutableBytes { buf in
        ch.writeSync(buf.baseAddress!, length: UInt16(frame.count))
    }

    if wstatus == 0 {
        print("[*] Sent successfully. Listening for response (5 sec)...")
    } else {
        print("[!] Write failed (status: \(wstatus))")
    }

    // Listen for response
    Thread.sleep(forTimeInterval: 5.0)

    if !del15.receivedData.isEmpty {
        print("[*] ch15 response so far: \(del15.receivedData.map { String(format: "%02x", $0) }.joined(separator: " "))")
    }
    if !del12.receivedData.isEmpty {
        print("[*] ch12 response so far: \(del12.receivedData.map { String(format: "%02x", $0) }.joined(separator: " "))")
    }
    print("---")
}

// Final check
print("\n[*] Final results:")
if del15.receivedData.isEmpty && del12.receivedData.isEmpty {
    print("[*] No response on either channel. Buds do not return touch config.")
    print("[*] Will need to use gesture settings screen as fallback.")
} else {
    if !del15.receivedData.isEmpty {
        print("[*] ch15 total response (\(del15.receivedData.count) bytes):")
        print("    \(del15.receivedData.map { String(format: "%02x", $0) }.joined(separator: " "))")
    }
    if !del12.receivedData.isEmpty {
        print("[*] ch12 total response (\(del12.receivedData.count) bytes):")
        print("    \(del12.receivedData.map { String(format: "%02x", $0) }.joined(separator: " "))")
    }
}

// Close
ch.close()
if let c = ch12 { c.close() }
Thread.sleep(forTimeInterval: 1)
print("[*] Done")
