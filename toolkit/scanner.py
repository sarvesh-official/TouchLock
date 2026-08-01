#!/usr/bin/env python3
"""Scan for BLE devices and dump the full GATT tree of the Realme Buds Air 8.

Usage:
    python toolkit/scanner.py                 # scan and list devices
    python toolkit/scanner.py <MAC_OR_NAME>   # connect + dump GATT tree

Output:
    docs/captures/air8_gatt_tree_bleak.txt
"""
import asyncio
import sys
from pathlib import Path
from datetime import datetime

from bleak import BleakClient, BleakScanner
from bleak.backends.characteristic import BleakGATTCharacteristic

CAPTURES_DIR = Path(__file__).resolve().parent.parent / "docs" / "captures"
CAPTURES_DIR.mkdir(parents=True, exist_ok=True)

# Known Realme Buds Air 8 MAC from nRF Connect recon
AIR8_MAC = "60:55:56:B9:32:70"


def prop_str(ch: BleakGATTCharacteristic) -> str:
    props = []
    if "read" in ch.properties: props.append("READ")
    if "write" in ch.properties: props.append("WRITE")
    if "write-without-response" in ch.properties: props.append("WRITE_NR")
    if "notify" in ch.properties: props.append("NOTIFY")
    if "indicate" in ch.properties: props.append("INDICATE")
    return ", ".join(props)


async def scan(timeout: float = 10.0):
    print(f"Scanning for {timeout}s...")
    devices = await BleakScanner.discover(timeout=timeout)
    found = []
    for d in devices:
        rssi = getattr(d, "rssi", None)
        line = f"{d.address}  {d.name!r}  rssi={rssi}"
        print(line)
        found.append(line)
    return found


async def dump_gatt(address: str):
    out_path = CAPTURES_DIR / "air8_gatt_tree_bleak.txt"
    print(f"Connecting to {address}...")
    async with BleakClient(address, timeout=20.0) as client:
        print(f"Connected. MTU={client.mtu_size}")
        lines = []
        lines.append(f"# Realme Buds Air 8 — GATT tree (bleak)")
        lines.append(f"# Captured: {datetime.now().isoformat()}")
        lines.append(f"# Address: {address}")
        lines.append(f"# MTU: {client.mtu_size}")
        lines.append("")
        for service in client.services:
            lines.append(f"## Service {service.uuid}  ({service.description or 'unknown'})")
            lines.append(f"   type: {service.service_type if hasattr(service, 'service_type') else 'primary'}")
            for ch in service.characteristics:
                lines.append(f"   - char {ch.uuid}  [{prop_str(ch)}]  handle={ch.handle}")
                for desc in ch.descriptors:
                    lines.append(f"       desc {desc.uuid}  handle={desc.handle}")
            lines.append("")
        text = "\n".join(lines)
        out_path.write_text(text)
        print(f"\nWrote GATT tree to {out_path}")
        print("\n--- preview ---")
        for line in lines[:60]:
            print(line)


async def main():
    args = sys.argv[1:]
    if not args:
        await scan()
        print(f"\nRe-run with: python toolkit/scanner.py {AIR8_MAC}")
        return
    target = args[0]
    # Allow fuzzy name match only if it doesn't look like a UUID/MAC
    looks_like_addr = (":" in target) or target.lower().startswith("0x") or ("-" in target and len(target) >= 32)
    if not looks_like_addr:
        devices = await BleakScanner.discover(timeout=10.0)
        match = next((d for d in devices if d.name and target.lower() in d.name.lower()), None)
        if not match:
            print(f"No device name matching {target!r}")
            return
        target = match.address
        print(f"Matched: {match.name} -> {target}")
    await dump_gatt(target)


if __name__ == "__main__":
    asyncio.run(main())
