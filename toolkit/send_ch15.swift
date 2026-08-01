#!/usr/bin/env swift
// Sends the full Gadgetbridge init sequence to channel 15 (OPOv1) and waits for responses.
// Usage: swift send_ch15.swift [lock|restore|status|init]

import Foundation
import IOBluetooth

let budsAddr = "60:55:56:B9:32:70"
let channelID: BluetoothRFCOMMChannelID = 15

func buildFrame(cmdId: UInt16, seq: UInt8, payload: [UInt8]) -> [UInt8] {
    // Gadgetbridge format: AA [len] 00 00 [cmdId LE] [seq] [payloadLen LE] [payload]
    let payloadLen = UInt16(payload.count)
    let totalLen = 7 + payload.count // 2 (zero) + 2 (cmd) + 1 (seq) + 2 (payloadLen) + payload
    var frame: [UInt8] = [0xAA, UInt8(totalLen), 0x00, 0x00]
    frame.append(UInt8(cmdId & 0xFF))
    frame.append(UInt8((cmdId >> 8) & 0xFF))
    frame.append(seq)
    frame.append(UInt8(payloadLen & 0xFF))
    frame.append(UInt8((payloadLen >> 8) & 0xFF))
    frame.append(contentsOf: payload)
    return frame
}

func parseFrame(_ data: [UInt8]) -> (cmdId: Int, seq: Int, payload: [UInt8])? {
    guard data.count >= 9, data[0] == 0xAA else { return nil }
    let totalLen = Int(data[1])
    guard data.count >= totalLen + 2 else { return nil }
    // bytes 2,3 are zero (or 4 on realme)
    let cmdId = Int(data[4]) | (Int(data[5]) << 8)
    let seq = Int(data[6])
    let payloadLen = Int(data[7]) | (Int(data[8]) << 8)
    guard 9 + payloadLen <= data.count else { return nil }
    let payload = Array(data[9..<(9 + payloadLen)])
    return (cmdId, seq, payload)
}

class RFCommDelegate: NSObject, IOBluetoothRFCOMMChannelDelegate {
    var receivedData: [UInt8] = []
    var openStatus: IOReturn = -1
    let semaphore = DispatchSemaphore(value: 0)

    func rfcommChannelOpenComplete(_ rfcommChannel: IOBluetoothRFCOMMChannel!, status error: IOReturn) {
        openStatus = error
        print("    [callback] open complete, status=\(error)")
        semaphore.signal()
    }

    func rfcommChannelClosed(_ rfcommChannel: IOBluetoothRFCOMMChannel!) {
        print("    [callback] closed")
    }

    func rfcommChannelData(_ rfcommChannel: IOBluetoothRFCOMMChannel!, data dataPointer: UnsafeMutableRawPointer!, length dataLength: Int) {
        let bytes = dataPointer.assumingMemoryBound(to: UInt8.self)
        let data = Array(UnsafeBufferPointer(start: bytes, count: dataLength))
        receivedData.append(contentsOf: data)
        print("    [callback] received \(dataLength) bytes: \(data.prefix(60).map { String(format: "%02x", $0) }.joined(separator: " "))")
    }

    func rfcommChannelWriteComplete(_ rfcommChannel: IOBluetoothRFCOMMChannel!, refcon: UnsafeMutableRawPointer!, status error: IOReturn) {
        print("    [callback] write complete, status=\(error)")
    }
}

guard let device = IOBluetoothDevice(addressString: budsAddr) else {
    print("[!] Could not find device")
    exit(1)
}

print("[*] Device: \(device.name ?? "?") connected=\(device.isConnected()) paired=\(device.isPaired())")

if !device.isConnected() {
    print("[*] Connecting...")
    _ = device.openConnection()
    Thread.sleep(forTimeInterval: 2)
}

let delegate = RFCommDelegate()
var channel: IOBluetoothRFCOMMChannel?

// Prime the connection by opening channel 12 (TOTA) first
print("[*] Priming connection by opening channel 12 (TOTA)...")
var primeChannel: IOBluetoothRFCOMMChannel?
let primeDelegate = RFCommDelegate()
let primeStatus = device.openRFCOMMChannelSync(&primeChannel, withChannelID: 12, delegate: primeDelegate)
print("[*] Channel 12 status: \(primeStatus)")
if let pc = primeChannel, pc.isOpen() {
    print("[*] Channel 12 open, waiting 2s...")
    Thread.sleep(forTimeInterval: 2.0)
}

print("[*] Opening channel \(channelID)...")
let status = device.openRFCOMMChannelSync(&channel, withChannelID: channelID, delegate: delegate)
print("[*] status: \(status)")

guard let ch = channel, ch.isOpen() else {
    print("[!] Channel not open")
    if let c = channel { c.close() }
    if let pc = primeChannel { pc.close() }
    exit(1)
}

print("[*] Channel \(channelID) is OPEN!")

// Wait for initial burst
print("[*] Waiting 3s for initial burst...")
Thread.sleep(forTimeInterval: 3.0)
if !delegate.receivedData.isEmpty {
    print("[*] Initial burst: \(delegate.receivedData.count) bytes")
    print("    \(delegate.receivedData.prefix(80).map { String(format: "%02x", $0) }.joined(separator: " "))")
    // Parse any frames
    var offset = 0
    while offset < delegate.receivedData.count {
        if delegate.receivedData[offset] != 0xAA { offset += 1; continue }
        let frameData = Array(delegate.receivedData[offset...])
        if let parsed = parseFrame(frameData) {
            print("    frame: cmdId=0x\(String(parsed.cmdId, radix: 16)) seq=\(parsed.seq) payloadLen=\(parsed.payload.count) payload=\(parsed.payload.prefix(20).map { String(format: "%02x", $0) }.joined(separator: " "))")
            if parsed.cmdId > 0 && parsed.cmdId < 0x10000 {
                let totalLen = Int(delegate.receivedData[offset + 1])
                offset += totalLen + 2
            } else {
                offset += 1
            }
        } else {
            offset += 1
        }
    }
    delegate.receivedData.removeAll()
} else {
    print("[*] No initial burst")
}

// Send init sequence
let action = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "init"
print("\n[*] Action: \(action)")

var seq: UInt8 = 1
let initCommands: [(UInt16, [UInt8], String)] = [
    (0x0205, [0x09, 0x01], "SUBSCRIPTION_SET (battery)"),
    (0x0106, [], "BATTERY_REQ"),
    (0x0108, [0x02, 0x03, 0x01], "TOUCH_CONFIG_REQ"),
    (0x0105, [], "FIRMWARE_GET"),
]

for (cmdId, payload, label) in initCommands {
    let frame = buildFrame(cmdId: cmdId, seq: seq, payload: payload)
    print("\n[*] Sending \(label) (0x\(String(cmdId, radix: 16))): \(frame.map { String(format: "%02x", $0) }.joined(separator: " "))")
    var data = Data(frame)
    let wstatus: IOReturn = data.withUnsafeMutableBytes { buf in
        ch.writeSync(buf.baseAddress!, length: UInt16(frame.count))
    }
    print("[*] Write status: \(wstatus)")
    seq += 1
    Thread.sleep(forTimeInterval: 2.0)
    if !delegate.receivedData.isEmpty {
        print("[*] Response: \(delegate.receivedData.count) bytes")
        print("    \(delegate.receivedData.prefix(80).map { String(format: "%02x", $0) }.joined(separator: " "))")
        // Parse frames
        var offset = 0
        while offset < delegate.receivedData.count {
            if delegate.receivedData[offset] != 0xAA { offset += 1; continue }
            let totalLen = Int(delegate.receivedData[offset + 1])
            let end = offset + totalLen + 2
            if end > delegate.receivedData.count { break }
            let frameData = Array(delegate.receivedData[offset..<end])
            if let parsed = parseFrame(frameData) {
                print("    frame: cmdId=0x\(String(parsed.cmdId, radix: 16)) seq=\(parsed.seq) payload=\(parsed.payload.map { String(format: "%02x", $0) }.joined(separator: " "))")
            }
            offset = end
        }
        delegate.receivedData.removeAll()
    } else {
        print("[*] No response")
    }
}

// Send the action command
if action == "lock" || action == "restore" || action == "status" {
    print("\n[*] Sending \(action.uppercased()) command...")
    let frame: [UInt8]
    if action == "lock" {
        // TOUCH_CONFIG_SET (0x0401): payload = [count] [side, type LE, value] * count
        // Set all 8 gestures (L/R x 2tap/3tap/hold/unk1) to OFF (0x00)
        var payload: [UInt8] = [0x08]
        let entries: [(UInt8, UInt16, UInt8)] = [
            (0x01, 0x0101, 0x00), (0x01, 0x0201, 0x00), (0x01, 0x0301, 0x00), (0x01, 0x0401, 0x00),
            (0x02, 0x0101, 0x00), (0x02, 0x0201, 0x00), (0x02, 0x0301, 0x00), (0x02, 0x0401, 0x00),
        ]
        for (side, type, value) in entries {
            payload.append(side)
            payload.append(UInt8(type & 0xFF))
            payload.append(UInt8((type >> 8) & 0xFF))
            payload.append(value)
        }
        frame = buildFrame(cmdId: 0x0401, seq: seq, payload: payload)
    } else if action == "restore" {
        // Restore default gestures
        var payload: [UInt8] = [0x08]
        let entries: [(UInt8, UInt16, UInt8)] = [
            (0x01, 0x0101, 0x01), (0x01, 0x0201, 0x06), (0x01, 0x0301, 0x05), (0x01, 0x0401, 0x03),
            (0x02, 0x0101, 0x01), (0x02, 0x0201, 0x06), (0x02, 0x0301, 0x05), (0x02, 0x0401, 0x03),
        ]
        for (side, type, value) in entries {
            payload.append(side)
            payload.append(UInt8(type & 0xFF))
            payload.append(UInt8((type >> 8) & 0xFF))
            payload.append(value)
        }
        frame = buildFrame(cmdId: 0x0401, seq: seq, payload: payload)
    } else {
        // status: TOUCH_CONFIG_REQ
        frame = buildFrame(cmdId: 0x0108, seq: seq, payload: [0x02, 0x03, 0x01])
    }
    print("[*] Frame: \(frame.map { String(format: "%02x", $0) }.joined(separator: " "))")
    var data = Data(frame)
    let wstatus: IOReturn = data.withUnsafeMutableBytes { buf in
        ch.writeSync(buf.baseAddress!, length: UInt16(frame.count))
    }
    print("[*] Write status: \(wstatus)")
    Thread.sleep(forTimeInterval: 5.0)
    if !delegate.receivedData.isEmpty {
        print("[*] Response: \(delegate.receivedData.count) bytes")
        print("    \(delegate.receivedData.prefix(80).map { String(format: "%02x", $0) }.joined(separator: " "))")
        var offset = 0
        while offset < delegate.receivedData.count {
            if delegate.receivedData[offset] != 0xAA { offset += 1; continue }
            let totalLen = Int(delegate.receivedData[offset + 1])
            let end = offset + totalLen + 2
            if end > delegate.receivedData.count { break }
            let frameData = Array(delegate.receivedData[offset..<end])
            if let parsed = parseFrame(frameData) {
                print("    frame: cmdId=0x\(String(parsed.cmdId, radix: 16)) seq=\(parsed.seq) payload=\(parsed.payload.map { String(format: "%02x", $0) }.joined(separator: " "))")
                if parsed.cmdId == 0x8401 {
                    let status = parsed.payload.first ?? 0xFF
                    print("    *** TOUCH-LOCK ACK! status=\(status) ***")
                }
            }
            offset = end
        }
    } else {
        print("[*] No response")
    }
}

print("\n[*] Closing channels...")
ch.close()
if let pc = primeChannel { pc.close() }
Thread.sleep(forTimeInterval: 1)
print("[*] Done")
