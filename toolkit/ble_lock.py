#!/usr/bin/env python3
"""
Sends the touch-lock command to Realme Buds Air 8 via BLE GATT.
Uses the 01000100 service's write characteristic (03000300-...).
"""

import asyncio
from bleak import BleakClient, BleakScanner
from protocol import build_touch_lock_command, build_restore_command, build_packet, build_oppov1_frame, parse_response

DEVICE_NAME = "realme"

# The 01000100 service — OPOv1-like layout
WRITE_CHAR = "03000300-0000-1000-8000-009278563412"
NOTIFY_CHAR = "02000200-0000-1000-8000-009178563412"

# Also try the 66666666 service as fallback
WRITE_CHAR_66 = "77777777-7777-7777-7777-777777777777"
NOTIFY_CHAR_66 = "77777777-7777-7777-7777-777777777777"

received_data = []


def notification_handler(sender, data):
    print(f"  [NOTIFY] {len(data)} bytes: {data[:60].hex(' ')}{'...' if len(data) > 60 else ''}")
    received_data.append(data)
    if len(data) > 0 and data[0] == 0xAA:
        parsed = parse_response(data)
        if parsed:
            cmd, tid, payload = parsed
            print(f"    *** OPOv1 frame! cmdId=0x{cmd:04X} tid={tid} payloadLen={len(payload)} ***")


async def main():
    import sys
    action = sys.argv[1].lower() if len(sys.argv) > 1 else "lock"

    print(f"[*] Action: {action}")

    print("[*] Scanning for Realme Buds Air 8...")
    devices = await BleakScanner.discover(timeout=10.0)
    target = None
    for d in devices:
        name = d.name or ""
        if "realme" in name.lower():
            target = d
            break

    if target is None:
        print(f"[!] Could not find Realme Buds device")
        for d in devices:
            print(f"    {d.name}: {d.address}")
        return

    print(f"[*] Found: {target.name} ({target.address})")

    print("[*] Connecting via BLE...")
    async with BleakClient(target.address, timeout=15.0) as client:
        print(f"[*] Connected: {client.is_connected}")

        # Try to subscribe to notify characteristic
        print(f"[*] Subscribing to notify char {NOTIFY_CHAR}...")
        try:
            await client.start_notify(NOTIFY_CHAR, notification_handler)
            print("  Subscribed OK")
        except Exception as e:
            print(f"  Failed: {e}")
            print("  (Commands may still work without notification subscription)")

        await asyncio.sleep(0.5)

        # Send initialization commands first (like Gadgetbridge)
        print("[*] Sending initialization sequence...")
        init_commands = [
            (0x0205, bytes([0x09, 0x01]), "SUBSCRIPTION_SET (battery)"),
            (0x0106, b"", "BATTERY_REQ"),
            (0x0108, bytes([0x02, 0x03, 0x01]), "TOUCH_CONFIG_REQ"),
            (0x0105, b"", "FIRMWARE_GET"),
        ]

        seq = 1
        for cmd_id, payload, label in init_commands:
            packet = build_packet(cmd_id, seq, payload)
            frame = build_oppov1_frame(packet)[0]
            print(f"  {label} (0x{cmd_id:04X}): {frame.hex(' ')}")
            try:
                await client.write_gatt_char(WRITE_CHAR, frame, response=True)
                print(f"    write: OK")
            except Exception as e:
                print(f"    write error: {e}")
            seq += 1
            await asyncio.sleep(0.5)

        # Check for any responses
        if received_data:
            print(f"\n[*] Got {len(received_data)} notifications during init!")
            for data in received_data:
                print(f"  {data.hex(' ')}")
        else:
            print("\n[*] No notifications received during init")

        # Now send the main command
        print(f"\n[*] Sending {action.upper()} command...")
        if action == "lock":
            frame = build_touch_lock_command(transfer_id=seq)
            label = "TOUCH LOCK (all gestures OFF)"
        elif action == "restore":
            frame = build_restore_command(transfer_id=seq)
            label = "RESTORE (default gestures)"
        elif action == "status":
            packet = build_packet(0x0108, seq, bytes([0x02, 0x03, 0x01]))
            frame = build_oppov1_frame(packet)[0]
            label = "GET TOUCH CONFIG"
        else:
            print(f"[!] Unknown action: {action}")
            return

        print(f"  {label}")
        print(f"  {frame.hex(' ')}")

        try:
            await client.write_gatt_char(WRITE_CHAR, frame, response=True)
            print(f"  write: OK")
        except Exception as e:
            print(f"  write error: {e}")

        # Wait for response
        print("[*] Waiting for response (5s)...")
        await asyncio.sleep(5.0)

        if received_data:
            print(f"\n[*] Got {len(received_data)} notifications!")
            for data in received_data:
                print(f"  {data.hex(' ')}")
                if len(data) > 0 and data[0] == 0xAA:
                    parsed = parse_response(data)
                    if parsed:
                        cmd, tid, payload = parsed
                        print(f"    cmdId=0x{cmd:04X} tid={tid} payload={payload.hex(' ')}")
                        if cmd == 0x8401:
                            status = payload[0] if payload else "?"
                            print(f"    *** TOUCH-LOCK RESPONSE! status={status} ***")
        else:
            print("[*] No notifications received")
            print("[*] The command may have been processed anyway.")
            print("[*] Please test the buds — try tapping/double-tapping/long-pressing them.")

        # Unsubscribe
        try:
            await client.stop_notify(NOTIFY_CHAR)
        except:
            pass

    print("[*] Done")


if __name__ == "__main__":
    asyncio.run(main())
