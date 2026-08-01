#!/usr/bin/env swift
// Tries opening each RFCOMM channel on the Realme Buds Air 8 and sends an OPOv1 BATTERY_REQ.
// Usage: swift try_channels.swift

import Foundation
import IOBluetooth

let budsAddr = "60:55:56:B9:32:70"

// Channels to try (from SDP browse)
let channels: [BluetoothRFCOMMChannelID] = [1, 12, 13, 15, 17, 24, 29]

// OPOv1 BATTERY_REQ frame: AA 07 00 00 06 01 01 00 00
let batteryReq: [UInt8] = [0xAA, 0x07, 0x00, 0x00, 0x06, 0x01, 0x01, 0x00, 0x00]

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
        print("    [callback] received \(dataLength) bytes: \(data.prefix(40).map { String(format: "%02x", $0) }.joined(separator: " "))")
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

for ch in channels {
    print("\n=== Trying channel \(ch) ===")
    let delegate = RFCommDelegate()
    var channel: IOBluetoothRFCOMMChannel?
    let status = device.openRFCOMMChannelSync(&channel, withChannelID: ch, delegate: delegate)
    print("[*] openRFCOMMChannelSync status: \(status)")

    if let c = channel, c.isOpen() {
        print("[*] Channel \(ch) is OPEN!")
        // Wait for any initial burst
        Thread.sleep(forTimeInterval: 1.0)
        if !delegate.receivedData.isEmpty {
            print("[*] Initial burst: \(delegate.receivedData.count) bytes")
            print("    \(delegate.receivedData.prefix(40).map { String(format: "%02x", $0) }.joined(separator: " "))")
            delegate.receivedData.removeAll()
        }

        // Send BATTERY_REQ
        var data = Data(batteryReq)
        print("[*] Sending BATTERY_REQ: \(batteryReq.map { String(format: "%02x", $0) }.joined(separator: " "))")
        let wstatus: IOReturn = data.withUnsafeMutableBytes { buf in
            c.writeSync(buf.baseAddress!, length: UInt16(batteryReq.count))
        }
        print("[*] Write status: \(wstatus)")

        // Wait for response
        Thread.sleep(forTimeInterval: 3.0)
        if !delegate.receivedData.isEmpty {
            print("[***] GOT RESPONSE on channel \(ch)!")
            print("    \(delegate.receivedData.prefix(60).map { String(format: "%02x", $0) }.joined(separator: " "))")
        } else {
            print("[*] No response on channel \(ch)")
        }

        c.close()
        Thread.sleep(forTimeInterval: 1.0)
    } else {
        print("[!] Channel \(ch) not open")
        if let c = channel { c.close() }
    }
}

print("\n[*] Done")
