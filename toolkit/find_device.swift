#!/usr/bin/env swift
// Sends FIND_DEVICE_REQ to channel 15 — the buds should beep if they receive it.
import Foundation
import IOBluetooth

let budsAddr = "60:55:56:B9:32:70"

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

    func rfcommChannelOpenComplete(_ rfcommChannel: IOBluetoothRFCOMMChannel!, status error: IOReturn) {
        print("    [callback] open complete, status=\(error)")
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

print("[*] Device: \(device.name ?? "?") connected=\(device.isConnected())")

// Try each channel with FIND_DEVICE_REQ
let channels: [BluetoothRFCOMMChannelID] = [12, 13, 15, 17, 24, 29]
let findDeviceFrame = buildFrame(cmdId: 0x0400, seq: 1, payload: [0x01])

for ch_id in channels {
    print("\n=== Channel \(ch_id) ===")
    let delegate = RFCommDelegate()
    var channel: IOBluetoothRFCOMMChannel?

    // Prime with channel 12 first (except when ch_id is 12 itself)
    if ch_id != 12 {
        print("[*] Priming with channel 12...")
        var prime: IOBluetoothRFCOMMChannel?
        let primeDel = RFCommDelegate()
        _ = device.openRFCOMMChannelSync(&prime, withChannelID: 12, delegate: primeDel)
        Thread.sleep(forTimeInterval: 1.0)
        if let p = prime { p.close() }
        Thread.sleep(forTimeInterval: 0.5)
    }

    let status = device.openRFCOMMChannelSync(&channel, withChannelID: ch_id, delegate: delegate)
    print("[*] Open status: \(status)")

    if let ch = channel, ch.isOpen() {
        print("[*] Channel \(ch_id) OPEN! Sending FIND_DEVICE_REQ (buds should beep)...")
        print("    Frame: \(findDeviceFrame.map { String(format: "%02x", $0) }.joined(separator: " "))")
        var data = Data(findDeviceFrame)
        let wstatus: IOReturn = data.withUnsafeMutableBytes { buf in
            ch.writeSync(buf.baseAddress!, length: UInt16(findDeviceFrame.count))
        }
        print("[*] Write status: \(wstatus)")

        // Wait for response and/or beep
        print("[*] Waiting 5s for response/beep...")
        Thread.sleep(forTimeInterval: 5.0)

        if !delegate.receivedData.isEmpty {
            print("[*] Got response: \(delegate.receivedData.count) bytes")
        } else {
            print("[*] No response data")
        }

        ch.close()
        Thread.sleep(forTimeInterval: 1.0)
    } else {
        print("[!] Channel \(ch_id) not open")
        if let c = channel { c.close() }
    }
}

print("\n[*] Done — did the buds beep on any channel?")
