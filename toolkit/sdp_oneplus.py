#!/usr/bin/env python3
"""Browse SDP records for a given Bluetooth address."""
import sys
import IOBluetooth

addr = sys.argv[1] if len(sys.argv) > 1 else "60-55-56-2a-55-7a"

dev = IOBluetooth.IOBluetoothDevice.withAddressString_(addr)
if not dev:
    print(f"[!] Could not find device {addr}")
    sys.exit(1)

print(f"Device: {dev.name()} ({addr})")
print(f"Connected: {dev.isConnected()}, Paired: {dev.isPaired()}")
print()

if not dev.isConnected():
    print("[*] Connecting...")
    dev.openConnection()
    import time
    time.sleep(3)
    print(f"Connected now: {dev.isConnected()}")

records = dev.services()
print(f"\nSDP Services ({len(records) if records else 0} total):\n")

if not records:
    print("No services found — device may need to be connected")
    sys.exit(0)

for i, record in enumerate(records):
    try:
        name = record.getServiceName()
    except:
        name = "(no name)"
    try:
        ch = record.getRFCOMMChannelID_()
        ch_str = f"RFCOMM channel {ch}"
    except:
        try:
            psm = record.getL2CAPPSM()
            ch_str = f"L2CAP PSM {psm}"
        except:
            ch_str = "no transport"
    print(f"  Service {i}: {name} — {ch_str}")
