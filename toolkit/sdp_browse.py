#!/usr/bin/env python3
"""
Browse the SDP records of the Realme Buds Air 8 to find all RFCOMM services
and their UUIDs. This helps us determine which SPP channel Realme Link uses
vs which one macOS connected to for /dev/cu.realmeBudsAir8.
"""

import IOBluetooth
import objc
from Foundation import NSString, NSMutableData

BUDS_ADDR = "60:55:56:B9:32:70"

# UUIDs we're looking for
STANDARD_SPP = "00001101-0000-1000-8000-00805f9b34fb"
CUSTOM_SPP_1 = "db764ac8-4b08-7f25-aafe-59d03c27bae3"
CUSTOM_SPP_2 = "db764ac8-4b08-7f25-aafe-59d03c27bae4"


def browse_sdp():
    """Browse SDP records of the buds."""
    device = IOBluetooth.IOBluetoothDevice.withAddressString_(BUDS_ADDR)
    if device is None:
        print(f"[!] Could not find device {BUDS_ADDR}")
        return

    print(f"[*] Device: {device.name()} ({device.addressString()})")
    print(f"    Connected: {device.isConnected()}")
    print(f"    Paired: {device.isPaired()}")

    # Get SDP services
    services = device.services()
    if services is None:
        print("[!] No services found (device may not be connected via IOBluetooth)")
        return

    print(f"\n[*] Found {len(services)} services:")
    for i, service in enumerate(services):
        print(f"\n  Service {i}:")
        # Try to get the service UUID
        try:
            uuid = service.getUUID()
            if uuid:
                uuid_str = uuid.UUIDString()
                print(f"    UUID: {uuid_str}")
        except:
            pass

        # Try to get the service name
        try:
            name = service.getServiceName()
            if name:
                print(f"    Name: {name}")
        except:
            pass

        # Try to get RFCOMM channel
        try:
            channel = service.getRFCOMMChannelID()
            if channel:
                print(f"    RFCOMM Channel: {channel}")
        except:
            pass

        # Try to get L2CAP PSM
        try:
            psm = service.getL2CAPPSM()
            if psm:
                print(f"    L2CAP PSM: {psm}")
        except:
            pass


def search_sdp_by_uuid(uuid_str):
    """Search for a specific UUID in the device's SDP records."""
    device = IOBluetooth.IOBluetoothDevice.withAddressString_(BUDS_ADDR)
    if device is None:
        print(f"[!] Could not find device {BUDS_ADDR}")
        return None

    print(f"\n[*] Searching for UUID {uuid_str}...")

    # Perform SDP query
    sdp_query = IOBluetooth.IOBluetoothSDPServiceRecord.searchServicesForUUID_(
        IOBluetooth.IOBluetoothSDPUUID.uuidWithString_(uuid_str)
    )

    # Actually, the API is different. Let me try another approach.
    # Use the device's SDP service record
    services = device.services()
    if services is None:
        print("[!] No services available")
        return None

    for service in services:
        try:
            uuid = service.getUUID()
            if uuid and uuid.UUIDString().lower() == uuid_str.lower():
                print(f"    FOUND! Service: {service}")
                try:
                    channel = service.getRFCOMMChannelID()
                    print(f"    RFCOMM Channel: {channel}")
                    return channel
                except:
                    pass
        except:
            pass

    print(f"    UUID {uuid_str} not found in SDP records")
    return None


def list_all_sdp_details():
    """List all SDP service records with full details."""
    device = IOBluetooth.IOBluetoothDevice.withAddressString_(BUDS_ADDR)
    if device is None:
        print(f"[!] Could not find device {BUDS_ADDR}")
        return

    services = device.services()
    if services is None:
        # Try to perform an SDP query
        print("[*] No cached services, performing SDP query...")
        # Open a connection to trigger SDP discovery
        if not device.isConnected():
            print("[*] Connecting to device...")
            device.openConnection()

        # Wait a moment for SDP discovery
        import time
        time.sleep(2)

        services = device.services()
        if services is None:
            print("[!] Still no services after connection attempt")
            return

    print(f"\n[*] SDP Services ({len(services)} total):")
    for i in range(len(services)):
        service = services.objectAtIndex_(i)
        print(f"\n  --- Service {i} ---")
        print(f"    Type: {service.class__()}")
        print(f"    Description: {service.description()}")

        # Try various attributes
        for attr_name in [
            "getUUID",
            "getServiceName",
            "getRFCOMMChannelID",
            "getL2CAPPSM",
            "getServiceRecordHandle",
            "getServiceClassIDList",
        ]:
            try:
                method = getattr(service, attr_name, None)
                if method:
                    result = method()
                    if result is not None:
                        print(f"    {attr_name}: {result}")
            except Exception as e:
                pass


if __name__ == "__main__":
    print("=== Browsing SDP records for Realme Buds Air 8 ===")
    list_all_sdp_details()

    print("\n=== Searching for specific UUIDs ===")
    for uuid_name, uuid_str in [
        ("Standard SPP", STANDARD_SPP),
        ("Custom SPP 1", CUSTOM_SPP_1),
        ("Custom SPP 2", CUSTOM_SPP_2),
    ]:
        channel = search_sdp_by_uuid(uuid_str)
        if channel:
            print(f"  {uuid_name}: RFCOMM channel {channel}")
        else:
            print(f"  {uuid_name}: NOT FOUND")
