#!/usr/bin/env python3
"""Sniff BLE notifications from Realme Buds Air 8.

Connects, subscribes to every NOTIFY/INDICATE characteristic, and logs each
incoming packet with a timestamp to docs/captures/air8_capture_<timestamp>.log.

While this runs, tap the buds in patterns. Press Ctrl+C to stop.

Usage:
    python toolkit/sniffer.py                 # uses known MAC, runs 60s
    python toolkit/sniffer.py <MAC_OR_NAME>   # override target
    python toolkit/sniffer.py realme 90       # name + duration in seconds

Tap protocol to follow while running (do each, wait 3s between):
    1. Single tap right bud        -> say "R1" out loud (we'll correlate by time)
    2. Double tap right bud        -> "R2"
    3. Triple tap right bud        -> "R3"
    4. Long press right bud        -> "RL"
    5. Single tap left bud         -> "L1"
    6. Double tap left bud         -> "L2"
    7. Long press left bud         -> "LL"
"""
import asyncio
import sys
from pathlib import Path
from datetime import datetime

from bleak import BleakClient, BleakScanner
from bleak.backends.characteristic import BleakGATTCharacteristic

CAPTURES_DIR = Path(__file__).resolve().parent.parent / "docs" / "captures"
CAPTURES_DIR.mkdir(parents=True, exist_ok=True)

AIR8_MAC = "60:55:56:B9:32:70"

_log_lines: list[str] = []
_start: float = 0.0


def _hex(b: bytes) -> str:
    return " ".join(f"{x:02X}" for x in b)


def _on_notify(sender: BleakGATTCharacteristic, data: bytearray, service_uuid: str = ""):
    now = asyncio.get_event_loop().time()
    elapsed = now - _start
    ts = datetime.now().strftime("%H:%M:%S.%f")[:-3]
    svc = service_uuid or getattr(sender, "service_uuid", "?")
    char_uuid = sender.uuid
    line = f"{ts}  +{elapsed:7.3f}s  {svc} / {char_uuid}  NOTIFY  len={len(data):3d}  {_hex(bytes(data))}"
    print(line)
    _log_lines.append(line)


def make_handler(service_uuid: str):
    def handler(sender: BleakGATTCharacteristic, data: bytearray):
        _on_notify(sender, data, service_uuid)
    return handler


async def main():
    global _start
    args = sys.argv[1:]
    target = args[0] if args else AIR8_MAC
    duration = float(args[1]) if len(args) > 1 else 60.0

    # On macOS, CoreBluetooth UUIDs can go stale between scan and connect.
    # Use find_device_by_filter to get a fresh BLEDevice object and pass it
    # directly to BleakClient (not the address string).
    looks_like_addr = (":" in target) or target.lower().startswith("0x") or ("-" in target and len(target) >= 32)
    name_filter = target if not looks_like_addr else None

    if name_filter:
        print(f"Scanning for device matching name {name_filter!r} ...")
        device = await BleakScanner.find_device_by_filter(
            lambda d, ad: d.name is not None and name_filter.lower() in d.name.lower(),
            timeout=20.0,
        )
        if not device:
            print(f"No device matching {name_filter!r}")
            return
        print(f"Found: {device.name} -> {device.address}")
    else:
        # Address/UUID passed — scan and match by address
        print(f"Scanning to resolve address {target} ...")
        device = await BleakScanner.find_device_by_filter(
            lambda d, ad: d.address == target,
            timeout=20.0,
        )
        if not device:
            print(f"Device {target} not found")
            return
        print(f"Found: {device.name} -> {device.address}")

    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    out_path = CAPTURES_DIR / f"air8_capture_{stamp}.log"
    print(f"Connecting to {device.address} ...")
    print(f"Logging to: {out_path}")
    print("Press Ctrl+C to stop. Tap the buds now.\n")

    # Try to pair first (needed for encrypted characteristics on macOS)
    client = BleakClient(device, timeout=20.0)
    try:
        await client.pair()
        print("Paired successfully.")
    except Exception as e:
        print(f"Pair() returned: {e} (continuing anyway)")

    await client.connect()
    print(f"Connected. MTU={client.mtu_size}")

    try:
        _start = asyncio.get_event_loop().time()
        subscribed = 0
        for service in client.services:
            svc_uuid = str(service.uuid)
            for ch in service.characteristics:
                props = ch.properties
                if "notify" in props or "indicate" in props:
                    try:
                        await client.start_notify(ch.uuid, make_handler(svc_uuid))
                        subscribed += 1
                        print(f"  subscribed: {svc_uuid} / {ch.uuid}  [{','.join(props)}]")
                    except Exception as e:
                        print(f"  FAILED subscribe {ch.uuid}: {e}")
        print(f"\nSubscribed to {subscribed} characteristics. Listening for {duration}s...\n")

        try:
            await asyncio.sleep(duration)
            print("\nDuration elapsed, stopping...")
        except (KeyboardInterrupt, asyncio.CancelledError):
            print("\nStopping...")
    finally:
        try:
            await client.disconnect()
        except Exception:
            pass

    # write log
    header = [
        f"# Realme Buds Air 8 — BLE notification capture",
        f"# Captured: {datetime.now().isoformat()}",
        f"# Address: {target}",
        f"# Subscribed characteristics: {subscribed}",
        f"# Format: <walltime>  +<elapsed>s  <service-uuid> / <char-uuid>  NOTIFY  len=<n>  <hex bytes>",
        "",
    ]
    out_path.write_text("\n".join(header + _log_lines))
    print(f"Wrote {len(_log_lines)} packets to {out_path}")


if __name__ == "__main__":
    asyncio.run(main())
