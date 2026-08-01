#!/usr/bin/env swift
// Investigates channel 17 (RFCOMM COM) — the live command channel.
// Captures all data and tries various commands.
import Foundation
import IOBluetooth

let budsAddr = "60:55:56:B9:32:70"

class RFCommDelegate: NSObject, IOBluetoothRFCOMMChannelDelegate {
    var receivedData: [UInt8] = []
    var allPackets: [[UInt8]] = []
    let semaphore = DispatchSemaphore(value: 0)

    func rfcommChannelOpenComplete(_ rfcommChannel: IOBluetoothRFCOMMChannel!, status error: IOReturn) {
        print("    [open] status=\(error)")
    }

    func rfcommChannelClosed(_ rfcommChannel: IOBluetoothRFCOMMChannel!) {
        print("    [closed]")
    }

    func rfcommChannelData(_ rfcommChannel: IOBluetoothRFCOMMChannel!, data dataPointer: UnsafeMutableRawPointer!, length dataLength: Int) {
        let bytes = dataPointer.assumingMemoryBound(to: UInt8.self)
        let data = Array(UnsafeBufferPointer(start: bytes, count: dataLength))
        receivedData.append(contentsOf: data)
        allPackets.append(data)
        let hex = data.map { String(format: "%02x", $0) }.joined(separator: " ")
        print("    [recv \(dataLength)B] \(hex)")
    }

    func rfcommChannelWriteComplete(_ rfcommChannel: IOBluetoothRFCOMMChannel!, refcon: UnsafeMutableRawPointer!, status error: IOReturn) {
        print("    [write complete] status=\(error)")
    }
}

guard let device = IOBluetoothDevice(addressString: budsAddr) else {
    print("[!] Could not find device")
    exit(1)
}

print("[*] Device: \(device.name ?? "?") connected=\(device.isConnected())")

// Prime with channel 12
print("[*] Priming with channel 12...")
var prime: IOBluetoothRFCOMMChannel?
let primeDel = RFCommDelegate()
_ = device.openRFCOMMChannelSync(&prime, withChannelID: 12, delegate: primeDel)
Thread.sleep(forTimeInterval: 1.0)

// Open channel 17
print("[*] Opening channel 17...")
let delegate = RFCommDelegate()
var channel: IOBluetoothRFCOMMChannel?
let status = device.openRFCOMMChannelSync(&channel, withChannelID: 17, delegate: delegate)
print("[*] Open status: \(status)")

guard let ch = channel, ch.isOpen() else {
    print("[!] Channel 17 not open")
    if let c = channel { c.close() }
    if let p = prime { p.close() }
    exit(1)
}

print("[*] Channel 17 OPEN!")
print("[*] Waiting 5s for initial burst...")
Thread.sleep(forTimeInterval: 5.0)

print("\n[*] Initial packets received: \(delegate.allPackets.count)")
for (i, pkt) in delegate.allPackets.enumerated() {
    print("  Packet \(i): \(pkt.map { String(format: "%02x", $0) }.joined(separator: " "))")
}

// Now try sending various commands
print("\n[*] Testing different command formats...")

// The initial packets look like: [03] [seq] [00] [len] [data]
// Let's try sending in the same format

// Try 1: Send a simple ping-like command
let testCommands: [(String, [UInt8])] = [
    ("ping type 01", [0x01, 0x01, 0x00, 0x00]),
    ("ping type 02", [0x02, 0x01, 0x00, 0x00]),
    ("ping type 03", [0x03, 0x01, 0x00, 0x00]),
    ("query type 04", [0x04, 0x01, 0x00, 0x00]),
    ("query type 05", [0x05, 0x01, 0x00, 0x00]),
    ("OPOv1 BATTERY_REQ", [0xAA, 0x07, 0x00, 0x00, 0x06, 0x01, 0x01, 0x00, 0x00]),
    ("OPOv1 FIND_DEVICE", [0xAA, 0x08, 0x00, 0x00, 0x00, 0x04, 0x01, 0x01, 0x00, 0x01]),
]

delegate.allPackets.removeAll()
delegate.receivedData.removeAll()

for (label, cmd) in testCommands {
    print("\n[*] Sending \(label): \(cmd.map { String(format: "%02x", $0) }.joined(separator: " "))")
    let countBefore = delegate.allPackets.count
    var data = Data(cmd)
    let wstatus: IOReturn = data.withUnsafeMutableBytes { buf in
        ch.writeSync(buf.baseAddress!, length: UInt16(cmd.count))
    }
    print("[*] Write status: \(wstatus)")
    Thread.sleep(forTimeInterval: 3.0)
    let newPackets = delegate.allPackets.count - countBefore
    if newPackets > 0 {
        print("[*] Got \(newPackets) response packets:")
        for i in countBefore..<delegate.allPackets.count {
            print("  \(delegate.allPackets[i].map { String(format: "%02x", $0) }.joined(separator: " "))")
        }
    } else {
        print("[*] No response")
    }
}

print("\n[*] Closing...")
ch.close()
if let p = prime { p.close() }
Thread.sleep(forTimeInterval: 1)
print("[*] Done")
