#!/usr/bin/env python3
"""
Sends the touch-lock command to Realme Buds Air 8 via the macOS serial port
(/dev/cu.realmeBudsAir8), which is an RFCOMM SPP connection to the buds.

Usage:
    python3 send_command.py lock      # disable all touch gestures
    python3 send_command.py restore   # restore default gestures (best guess)
    python3 send_command.py status    # query current key function (cmd 0x0401 get)
    python3 send_command.py raw <hex> # send arbitrary hex bytes

Prerequisites:
    - Buds paired with Mac (System Settings > Bluetooth)
    - Buds connected (may need to connect via System Settings or `blueutil`)
    - /dev/cu.realmeBudsAir8 exists (appears when buds are connected)
    - pip3 install pyserial
"""

import serial
import sys
import time
import struct
from protocol import (
    build_touch_lock_command,
    build_restore_command,
    build_packet,
    build_oppov1_frame,
    parse_response,
)

SERIAL_PORT = "/dev/cu.realmeBudsAir8"
BAUDRATE = 115200  # doesn't matter for Bluetooth SPP, but pyserial needs something
TIMEOUT = 5.0  # seconds to wait for response


def open_port():
    """Open the serial port to the buds."""
    try:
        ser = serial.Serial(
            port=SERIAL_PORT,
            baudrate=BAUDRATE,
            timeout=TIMEOUT,
            write_timeout=TIMEOUT,
        )
        print(f"[*] Opened {SERIAL_PORT}")
        return ser
    except serial.SerialException as e:
        print(f"[!] Failed to open {SERIAL_PORT}: {e}")
        print(f"[!] Make sure the buds are connected via System Settings > Bluetooth")
        sys.exit(1)


def send_and_receive(ser, frame: bytes, label: str = "command") -> bytes | None:
    """Send a frame and read the response."""
    print(f"[*] Sending {label} ({len(frame)} bytes):")
    print(f"    {frame.hex(' ')}")

    try:
        n = ser.write(frame)
        ser.flush()
        print(f"[*] Wrote {n} bytes")
    except Exception as e:
        print(f"[!] Write failed: {e}")
        return None

    # Read response — the buds typically respond within 1-2 seconds
    print(f"[*] Waiting for response (timeout={TIMEOUT}s)...")
    time.sleep(0.5)

    response = b""
    try:
        while True:
            chunk = ser.read(64)
            if not chunk:
                break
            response += chunk
            print(f"[*] Read {len(chunk)} bytes (total {len(response)})")
            if len(response) > 0 and response[0] != 0xAA:
                print(f"[!] Warning: response doesn't start with 0xAA: {response.hex(' ')}")
                break
            # Try to parse — if we have a complete frame, stop
            if len(response) >= 5:
                # Parse LEB128 length to see if we have the full frame
                idx = 1
                frame_len = 0
                shift = 0
                while idx < len(response):
                    b = response[idx]
                    frame_len |= (b & 0x7F) << (shift * 7)
                    idx += 1
                    shift += 1
                    if (b & 0x80) == 0:
                        break
                total_expected = idx + frame_len + 1  # +1 for SOF
                if len(response) >= total_expected:
                    break
    except Exception as e:
        print(f"[!] Read failed: {e}")

    if response:
        print(f"[*] Response ({len(response)} bytes): {response.hex(' ')}")
        parsed = parse_response(response)
        if parsed:
            cmd_id, transfer_id, payload = parsed
            print(f"[*] Parsed: cmdId=0x{cmd_id:04X} transferId={transfer_id} payload={payload.hex(' ')}")
            if len(payload) > 0:
                status = payload[0]
                print(f"[*] Status: {status} ({'SUCCESS' if status == 0 else 'ERROR'})")
        return response
    else:
        print("[*] No response received (timeout)")
        return None


def cmd_lock(ser):
    """Disable all touch gestures."""
    frame = build_touch_lock_command(transfer_id=1)
    send_and_receive(ser, frame, "TOUCH LOCK (all gestures OFF)")


def cmd_restore(ser):
    """Restore default touch gestures (best guess)."""
    frame = build_restore_command(transfer_id=2)
    send_and_receive(ser, frame, "RESTORE (default gestures)")


def cmd_status(ser):
    """Query current key function settings (command 0x0301 = get key function)."""
    # Protocol.z = 264 = 0x108 is "get key function"
    # Actually from the b2 array: {z, Z0} = {264, 1025}
    # 264 (0x108) is the GET, 1025 (0x401) is the SET
    packet = build_packet(0x0108, 1, b"")
    frames = build_oppov1_frame(packet)
    send_and_receive(ser, frames[0], "GET KEY FUNCTION (cmd 0x0108)")


def cmd_raw(ser, hex_str):
    """Send arbitrary hex bytes."""
    try:
        data = bytes.fromhex(hex_str.replace(" ", ""))
    except ValueError as e:
        print(f"[!] Invalid hex: {e}")
        return
    send_and_receive(ser, data, f"RAW ({len(data)} bytes)")


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    action = sys.argv[1].lower()

    ser = open_port()
    try:
        if action == "lock":
            cmd_lock(ser)
        elif action == "restore":
            cmd_restore(ser)
        elif action == "status":
            cmd_status(ser)
        elif action == "raw":
            if len(sys.argv) < 3:
                print("[!] Usage: send_command.py raw <hex bytes>")
                sys.exit(1)
            cmd_raw(ser, sys.argv[2])
        else:
            print(f"[!] Unknown action: {action}")
            print(__doc__)
            sys.exit(1)
    finally:
        ser.close()
        print(f"[*] Closed {SERIAL_PORT}")


if __name__ == "__main__":
    main()
