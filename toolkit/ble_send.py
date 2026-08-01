#!/usr/bin/env python3
"""
Tries sending OPOv1 commands to the Realme Buds Air 8 via BLE GATT.
Since macOS can't open RFCOMM channel 15, we try each BLE write characteristic
to find which one accepts OPOv1 commands.
"""

import asyncio
import struct
from bleak import BleakClient, BleakScanner
from protocol import build_packet, build_oppov1_frame, parse_response

DEVICE_NAME = "realme Buds Air8"

# BLE GATT characteristics from the GATT tree
WRITE_CHARS = {
    # Service 01000100 (OPOv1-like layout) — strongest candidate
    "03000300-0000-1000-8000-009278563412": "01000100 write (OPOv1-like)",
    # Service 66666666 (shared with Air 7)
    "77777777-7777-7777-7777-777777777777": "66666666 write (Air 7 shared)",
    # Service 65786365 (BES-distributor)
    "65786365-6c70-6f69-6e74-2e636f820002": "65786365 write 1",
    "65786365-6c70-6f69-6e74-2e636f820004": "65786365 write 2",
    # Service 86868686
    "97979797-9797-9797-9797-979797979797": "86868686 write",
    # Service 000008a4
    "00000001-0000-1000-8000-00805f9b34fb": "000008a4 write",
}

NOTIFY_CHARS = {
    "02000200-0000-1000-8000-009178563412": "01000100 notify",
    "77777777-7777-7777-7777-777777777777": "66666666 notify",
    "65786365-6c70-6f69-6e74-2e636f820001": "65786365 notify 1",
    "65786365-6c70-6f69-6e74-2e636f820003": "65786365 notify 2",
    "97979797-9797-9797-9797-979797979797": "86868686 notify",
    "00000002-0000-1000-8000-00805f9b34fb": "000008a4 notify",
    # Fast Pair notify chars (less likely but worth trying)
    "fe2c1234-8366-4814-8eb0-01de32100bea": "FastPair notify 1",
    "fe2c1235-8366-4814-8eb0-01de32100bea": "FastPair notify 2",
}

received_data = {}


def notification_handler(char_uuid, label):
    def handler(sender, data):
        print(f"  [NOTIFY {label}] {len(data)} bytes: {data[:40].hex(' ')}{'...' if len(data) > 40 else ''}")
        received_data[char_uuid] = data
        # Try to parse as OPOv1 frame
        if len(data) > 0 and data[0] == 0xAA:
            parsed = parse_response(data)
            if parsed:
                cmd, tid, payload = parsed
                print(f"    *** OPOv1 frame! cmdId=0x{cmd:04X} tid={tid} payloadLen={len(payload)} ***")
    return handler


async def try_send_commands(client, write_char, label):
    """Send OPOv1 commands to a write characteristic and check for responses."""
    print(f"\n{'='*60}")
    print(f"Testing {label}")
    print(f"Write char: {write_char}")
    print(f"{'='*60}")

    # Clear received data
    received_data.clear()

    # Build Gadgetbridge initialization commands
    commands = [
        (0x0205, bytes([0x09, 0x01]), "SUBSCRIPTION_SET (battery)"),
        (0x0106, b"", "BATTERY_REQ"),
        (0x0108, bytes([0x02, 0x03, 0x01]), "TOUCH_CONFIG_REQ"),
        (0x0105, b"", "FIRMWARE_GET"),
    ]

    for cmd_id, payload, cmd_label in commands:
        packet = build_packet(cmd_id, 1, payload)
        frame = build_oppov1_frame(packet)[0]
        print(f"\n  Sending {cmd_label} (0x{cmd_id:04X}): {frame.hex(' ')}")

        # Try write with response first
        try:
            await client.write_gatt_char(write_char, frame, response=True)
            print(f"    write (with response): OK")
        except Exception as e:
            print(f"    write (with response): {e}")
            # Try write without response
            try:
                await client.write_gatt_char(write_char, frame, response=False)
                print(f"    write (without response): OK")
            except Exception as e2:
                print(f"    write (without response): {e2}")
                continue

        # Wait for notification
        await asyncio.sleep(2.0)

        # Check if we got any data
        if received_data:
            for char_uuid, data in received_data.items():
                print(f"    *** GOT RESPONSE on {char_uuid}: {data.hex(' ')} ***")
            return True

    return False


async def try_hello_register(client, write_char, label):
    """Try the HELLO + REGISTER authentication sequence from Aasheesh's blog."""
    print(f"\n{'='*60}")
    print(f"Trying HELLO + REGISTER on {label}")
    print(f"{'='*60}")

    received_data.clear()

    # HELLO packet (from Aasheesh's blog)
    hello = bytes.fromhex("AA 07 00 00 00 01 23 00 00 12".replace(" ", ""))
    print(f"\n  Sending HELLO: {hello.hex(' ')}")
    try:
        await client.write_gatt_char(write_char, hello, response=False)
        print(f"    write: OK")
    except Exception as e:
        print(f"    write: {e}")
        return False

    await asyncio.sleep(2.0)
    if received_data:
        for char_uuid, data in received_data.items():
            print(f"    *** GOT RESPONSE on {char_uuid}: {data.hex(' ')} ***")

    # REGISTER packet (token B5 50 A0 69 from Aasheesh's blog for Nord Buds 3 Pro)
    register = bytes.fromhex("AA 0C 00 00 00 85 41 05 00 00 B5 50 A0 69".replace(" ", ""))
    print(f"\n  Sending REGISTER: {register.hex(' ')}")
    try:
        await client.write_gatt_char(write_char, register, response=False)
        print(f"    write: OK")
    except Exception as e:
        print(f"    write: {e}")
        return False

    await asyncio.sleep(2.0)
    if received_data:
        for char_uuid, data in received_data.items():
            print(f"    *** GOT RESPONSE on {char_uuid}: {data.hex(' ')} ***")
        return True

    return False


async def main():
    print("[*] Scanning for Realme Buds Air 8...")

    # Find the device
    devices = await BleakScanner.discover(timeout=10.0)
    target = None
    for d in devices:
        name = d.name or ""
        if "realme" in name.lower() and "buds" not in name.lower():
            # "realme " (truncated) — likely the buds
            target = d
            break
        if DEVICE_NAME in name:
            target = d
            break

    if target is None:
        print(f"[!] Could not find {DEVICE_NAME}")
        print("[*] Available devices:")
        for d in devices:
            print(f"    {d.name}: {d.address}")
        return

    print(f"[*] Found: {target.name} ({target.address})")

    # Connect
    print("[*] Connecting...")
    async with BleakClient(target.address, timeout=15.0) as client:
        print(f"[*] Connected: {client.is_connected}")
        print(f"[*] MTU: (unknown)")

        # Subscribe to all notify characteristics
        print("[*] Subscribing to notifications...")
        for char_uuid, label in NOTIFY_CHARS.items():
            try:
                await client.start_notify(char_uuid, notification_handler(char_uuid, label))
                print(f"  Subscribed to {label}")
            except Exception as e:
                print(f"  Failed to subscribe to {label}: {e}")

        await asyncio.sleep(1.0)

        # Try sending OPOv1 commands to each write characteristic
        for char_uuid, label in WRITE_CHARS.items():
            success = await try_send_commands(client, char_uuid, label)
            if success:
                print(f"\n*** FOUND WORKING CHANNEL: {label} ***")
                # If we found a working channel, send the touch-lock command
                print("\n[*] Sending TOUCH LOCK command...")
                from protocol import build_touch_lock_command
                lock_frame = build_touch_lock_command(transfer_id=1)
                print(f"    {lock_frame.hex(' ')}")
                try:
                    await client.write_gatt_char(char_uuid, lock_frame, response=False)
                    print(f"    write: OK")
                except Exception as e:
                    print(f"    write: {e}")
                await asyncio.sleep(3.0)
                if received_data:
                    for cu, data in received_data.items():
                        print(f"    *** RESPONSE: {data.hex(' ')} ***")
                break

        # If no direct OPOv1 commands worked, try HELLO + REGISTER
        print("\n[*] No direct OPOv1 response. Trying HELLO + REGISTER authentication...")
        for char_uuid, label in WRITE_CHARS.items():
            success = await try_hello_register(client, char_uuid, label)
            if success:
                print(f"\n*** FOUND WORKING CHANNEL with auth: {label} ***")
                break

        # Unsubscribe
        print("\n[*] Unsubscribing...")
        for char_uuid in NOTIFY_CHARS:
            try:
                await client.stop_notify(char_uuid)
            except:
                pass

    print("[*] Done")


if __name__ == "__main__":
    asyncio.run(main())
