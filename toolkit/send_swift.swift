#!/usr/bin/env swift
// Connects to Realme Buds Air 8 on RFCOMM channel 15 (OPOv1) and sends a touch-lock command.
// Usage: swift send_swift.swift [lock|restore|status|poll]

import Foundation
import IOBluetooth

let budsAddr = "60:55:56:B9:32:70"
let channelID: BluetoothRFCOMMChannelID = 15

// Build OPOv1 frame: AA [len LEB128] [ctrl] [00] [group] [cmd] [seq] [payloadLen LE] [payload]
func buildFrame(group: UInt8, cmd: UInt8, seq: UInt8, payload: [UInt8]) -> [UInt8] {
    let payloadLen = UInt16(payload.count)
    let remLen = 7 + payload.count // 2 (link header) + 5 (inner header) + payload
    var frame: [UInt8] = [0xAA]
    // LEB128 encode remLen
    var val = remLen
    while val > 0x7F {
        frame.append(UInt8(val & 0x7F) | 0x80)
        val >>= 7
    }
    frame.append(UInt8(val))
    // Link header
    frame.append(0x00) // ctrl (single frame)
    frame.append(0x00) // reserved
    // Inner header
    frame.append(group)
    frame.append(cmd)
    frame.append(seq)
    frame.append(UInt8(payloadLen & 0xFF))
    frame.append(UInt8((payloadLen >> 8) & 0xFF))
    // Payload
    frame.append(contentsOf: payload)
    return frame
}

// Touch-lock payload: [count] [earbud, action, trigger, function] * count
func buildTouchLockPayload() -> [UInt8] {
    let entries: [(UInt8, UInt8, UInt8, UInt8)] = [
        (1, 1, 1, 0), (1, 1, 2, 0), (1, 1, 3, 0), (1, 1, 4, 0),
        (2, 1, 1, 0), (2, 1, 2, 0), (2, 1, 3, 0), (2, 1, 4, 0),
    ]
    var payload: [UInt8] = [UInt8(entries.count)]
    for (dev, btn, act, func_) in entries {
        payload.append(contentsOf: [dev, btn, act, func_])
    }
    return payload
}

func buildRestorePayload() -> [UInt8] {
    let entries: [(UInt8, UInt8, UInt8, UInt8)] = [
        (1, 1, 1, 1), (1, 1, 2, 6), (1, 1, 3, 5), (1, 1, 4, 3),
        (2, 1, 1, 1), (2, 1, 2, 6), (2, 1, 3, 5), (2, 1, 4, 3),
    ]
    var payload: [UInt8] = [UInt8(entries.count)]
    for (dev, btn, act, func_) in entries {
        payload.append(contentsOf: [dev, btn, act, func_])
    }
    return payload
}

// Parse response frame
func parseFrame(_ data: [UInt8]) -> (cmdId: Int, seq: Int, payload: [UInt8])? {
    guard data.count >= 7, data[0] == 0xAA else { return nil }
    var idx = 1
    var length = 0
    var shift = 0
    while idx < data.count {
        let b = data[idx]
        length |= (Int(b) & 0x7F) << shift
        idx += 1
        shift += 7
        if (b & 0x80) == 0 { break }
    }
    let ctrl = data[idx]; idx += 1
    idx += 1 // reserved
    if (ctrl & 0x03) != 0 { idx += 1 } // FSN
    guard idx + 5 <= data.count else { return nil }
    let group = data[idx]
    let cmd = data[idx + 1]
    let seq = data[idx + 2]
    let payloadLen = Int(data[idx + 3]) | (Int(data[idx + 4]) << 8)
    let payload = Array(data[(idx + 5)..<(idx + 5 + payloadLen)])
    let cmdId = (Int(cmd) << 8) | Int(group)
    return (cmdId, Int(seq), payload)
}

// Delegate class for RFCOMM channel events
class RFCommDelegate: NSObject, IOBluetoothRFCOMMChannelDelegate {
    var receivedData: [UInt8] = []
    var channel: IOBluetoothRFCOMMChannel?
    let semaphore = DispatchSemaphore(value: 0)

    func rfcommChannelOpenComplete(_ rfcommChannel: IOBluetoothRFCOMMChannel!, status error: IOReturn) {
        print("  [callback] channel open complete, status=\(error) (0x\(String(error, radix: 16)))")
        if error == 0 {
            print("  [callback] CHANNEL OPEN SUCCESS!")
        }
        semaphore.signal()
    }

    func rfcommChannelClosed(_ rfcommChannel: IOBluetoothRFCOMMChannel!) {
        print("  [callback] channel closed")
    }

    func rfcommChannelData(_ rfcommChannel: IOBluetoothRFCOMMChannel!, data dataPointer: UnsafeMutableRawPointer!, length dataLength: Int) {
        let bytes = dataPointer.assumingMemoryBound(to: UInt8.self)
        let data = Array(UnsafeBufferPointer(start: bytes, count: dataLength))
        receivedData.append(contentsOf: data)
        print("  [callback] received \(dataLength) bytes")
    }

    func rfcommChannelWriteComplete(_ rfcommChannel: IOBluetoothRFCOMMChannel!, refcon: UnsafeMutableRawPointer!, status error: IOReturn) {
        print("  [callback] write complete, status=\(error)")
    }
}

// Main
let action = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "lock"

guard let device = IOBluetoothDevice(addressString: budsAddr) else {
    print("[!] Could not find device \(budsAddr)")
    exit(1)
}

print("[*] Device: \(device.name ?? "?") (\(device.addressString ?? "?"))")
print("[*] Connected: \(device.isConnected()), Paired: \(device.isPaired())")

if !device.isConnected() {
    print("[*] Connecting...")
    _ = device.openConnection()
    Thread.sleep(forTimeInterval: 2)
}

let delegate = RFCommDelegate()

print("[*] Opening RFCOMM channel \(channelID) (OPOv1)...")
var channel: IOBluetoothRFCOMMChannel?
let status = device.openRFCOMMChannelSync(&channel, withChannelID: channelID, delegate: delegate)

print("[*] openRFCOMMChannelSync status: \(status) (0x\(String(status, radix: 16)))")

if let ch = channel {
    print("[*] Channel: \(ch)")
    print("[*] Is open: \(ch.isOpen())")

    if ch.isOpen() {
        print("*** CHANNEL 15 IS OPEN! ***")
        delegate.channel = ch

        // Wait for connection burst
        print("[*] Waiting for connection burst...")
        Thread.sleep(forTimeInterval: 3)

        if !delegate.receivedData.isEmpty {
            print("[*] Connection burst: \(delegate.receivedData.count) bytes")
            print("    \(delegate.receivedData.prefix(60).map { String(format: "%02x", $0) }.joined(separator: " "))")
            delegate.receivedData.removeAll()
        } else {
            print("[*] No connection burst")
        }

        // Send command
        let frame: [UInt8]
        let label: String
        switch action {
        case "lock":
            frame = buildFrame(group: 0x01, cmd: 0x04, seq: 1, payload: buildTouchLockPayload())
            label = "TOUCH LOCK"
        case "restore":
            frame = buildFrame(group: 0x01, cmd: 0x04, seq: 2, payload: buildRestorePayload())
            label = "RESTORE"
        case "status":
            frame = buildFrame(group: 0x08, cmd: 0x01, seq: 1, payload: [0x02, 0x03])
            label = "GET GESTURES"
        case "poll":
            // Send multiple poll commands
            for (cmdId, cmdLabel) in [(0x0100, "init"), (0x0106, "battery"), (0x0107, "firmware")] {
                let g = UInt8(cmdId & 0xFF)
                let c = UInt8((cmdId >> 8) & 0xFF)
                let f = buildFrame(group: g, cmd: c, seq: 1, payload: [])
                print("\n[*] Sending poll \(cmdLabel) (0x\(String(cmdId, radix: 16))): \(f.map { String(format: "%02x", $0) }.joined(separator: " "))")
                var nsData = Data(f)
                let wstatus: IOReturn = nsData.withUnsafeMutableBytes { buf in ch.writeSync(buf.baseAddress!, length: UInt16(f.count)) }
                print("    write status: \(wstatus)")
                Thread.sleep(forTimeInterval: 3)
                if !delegate.receivedData.isEmpty {
                    let resp = delegate.receivedData
                    print("    response (\(resp.count) bytes): \(resp.prefix(60).map { String(format: "%02x", $0) }.joined(separator: " "))")
                    // Parse frames
                    var offset = 0
                    while offset < resp.count {
                        if resp[offset] != 0xAA { offset += 1; continue }
                        var idx = offset + 1
                        var flen = 0
                        var shift = 0
                        while idx < resp.count {
                            let b = resp[idx]
                            flen |= (Int(b) & 0x7F) << shift
                            idx += 1
                            shift += 7
                            if (b & 0x80) == 0 { break }
                        }
                        let total = idx + flen + 1
                        if total > resp.count { break }
                        let frameData = Array(resp[offset..<total])
                        if let parsed = parseFrame(frameData) {
                            print("    frame: cmdId=0x\(String(parsed.cmdId, radix: 16)) seq=\(parsed.seq) payloadLen=\(parsed.payload.count)")
                        }
                        offset = total
                    }
                    delegate.receivedData.removeAll()
                } else {
                    print("    no response")
                }
            }
            ch.close()
            Thread.sleep(forTimeInterval: 1)
            exit(0)
        default:
            print("[!] Unknown action: \(action)")
            ch.close()
            exit(1)
        }

        print("\n[*] Sending \(label) (\(frame.count) bytes):")
        print("    \(frame.map { String(format: "%02x", $0) }.joined(separator: " "))")

        var nsData = Data(frame)
        let wstatus: IOReturn = nsData.withUnsafeMutableBytes { buf in ch.writeSync(buf.baseAddress!, length: UInt16(frame.count)) }
        print("[*] Write status: \(wstatus)")

        // Wait for response
        print("[*] Waiting for response...")
        Thread.sleep(forTimeInterval: 5)

        if !delegate.receivedData.isEmpty {
            let resp = delegate.receivedData
            print("\n[*] Response (\(resp.count) bytes): \(resp.map { String(format: "%02x", $0) }.joined(separator: " "))")
            var offset = 0
            while offset < resp.count {
                if resp[offset] != 0xAA { offset += 1; continue }
                var idx = offset + 1
                var flen = 0
                var shift = 0
                while idx < resp.count {
                    let b = resp[idx]
                    flen |= (Int(b) & 0x7F) << shift
                    idx += 1
                    shift += 7
                    if (b & 0x80) == 0 { break }
                }
                let total = idx + flen + 1
                if total > resp.count { break }
                let frameData = Array(resp[offset..<total])
                if let parsed = parseFrame(frameData) {
                    print("    frame: cmdId=0x\(String(parsed.cmdId, radix: 16)) seq=\(parsed.seq) payload=\(parsed.payload.map { String(format: "%02x", $0) }.joined(separator: " "))")
                    if parsed.cmdId == 0x8401 {
                        let statusByte = parsed.payload.first ?? 0xFF
                        print("    *** TOUCH-LOCK RESPONSE! status=\(statusByte) ***")
                    }
                }
                offset = total
            }
        } else {
            print("[*] No response received")
        }

        // Close channel
        print("\n[*] Closing channel...")
        ch.close()
        Thread.sleep(forTimeInterval: 1)
    } else {
        print("[!] Channel is not open")
        print("[!] The buds may still be connected to your phone via Realme Link.")
        ch.close()
    }
} else {
    print("[!] No channel returned")
}
