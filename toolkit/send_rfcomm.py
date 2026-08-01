#!/usr/bin/env python3
"""
Connects to the Realme Buds Air 8's OPOv1 command channel (RFCOMM channel 15)
and sends the touch-lock command. Uses IOBluetooth via PyObjC.
"""

import sys
import time
import objc
from Foundation import NSObject, NSRunLoop, NSDate, NSData, NSMutableData
import IOBluetooth
from protocol import (
    build_touch_lock_command,
    build_restore_command,
    build_packet,
    build_oppov1_frame,
    parse_response,
)

BUDS_ADDR = "60:55:56:B9:32:70"
OPOV1_CHANNEL = 15


class RFCommDelegate(NSObject):
    """Delegate for IOBluetoothRFCOMMChannel events."""

    def init(self):
        self = objc.super(RFCommDelegate, self).init()
        if self is None:
            return None
        self.received_data = bytearray()
        self.write_status = None
        self.channel_opened = False
        self.channel_closed = False
        return self

    def rfcommChannelOpenComplete_status_(self, channel, status):
        print(f"  [callback] channel open complete, status={status}")
        self.channel_opened = (status == 0)

    def rfcommChannelClosed_(self, channel):
        print(f"  [callback] channel closed")
        self.channel_closed = True

    def rfcommChannelData_available_(self, channel, data):
        if data:
            length = data.length()
            bytes_ptr = data.bytes()
            raw = bytes((bytes_ptr[i] for i in range(length)))
            self.received_data.extend(raw)
            print(f"  [callback] received {length} bytes")
        return data.length() if data else 0

    def rfcommChannelWriteComplete_refcon_status_(self, channel, refcon, status):
        print(f"  [callback] write complete, status={status}")
        self.write_status = status

    def getReceivedData(self):
        return bytes(self.received_data)

    def clearReceivedData(self):
        self.received_data = bytearray()


def run_runloop(seconds):
    """Run the NSRunLoop for the given number of seconds."""
    end = time.time() + seconds
    while time.time() < end:
        NSRunLoop.currentRunLoop().runUntilDate_(
            NSDate.dateWithTimeIntervalSinceNow_(0.05)
        )


def connect_and_send(action="lock"):
    device = IOBluetooth.IOBluetoothDevice.withAddressString_(BUDS_ADDR)
    if device is None:
        print(f"[!] Could not find device {BUDS_ADDR}")
        return

    print(f"[*] Device: {device.name()} ({device.addressString()})")
    print(f"    Connected: {device.isConnected()}")

    if not device.isConnected():
        print("[*] Connecting...")
        device.openConnection_()
        run_runloop(2)

    # Create delegate
    delegate = RFCommDelegate.alloc().init()

    # Open RFCOMM channel 15 (OPOv1 command channel)
    print(f"[*] Opening RFCOMM channel {OPOV1_CHANNEL} (OPOv1)...")
    try:
        status, channel = device.openRFCOMMChannelAsync_withChannelID_delegate_(
            None, OPOV1_CHANNEL, delegate
        )
    except Exception as e:
        print(f"[!] Failed to open channel: {e}")
        return

    if status != 0:
        print(f"[!] Channel open failed, status={status}")
        return

    print(f"[*] Channel opened: {channel}")
    print(f"    Channel ID: {channel.channelID()}")
    print(f"    Is open: {channel.isOpen()}")

    # Wait for channel to fully open
    if not channel.isOpen():
        print("[*] Waiting for channel to open (up to 15s)...")
        for i in range(150):
            run_runloop(0.1)
            if channel.isOpen():
                break
        print(f"    Is open now: {channel.isOpen()}")

    if not channel.isOpen():
        print("[!] Channel still not open after 15s")
        print("[!] The buds may still be connected to your phone via Realme Link.")
        print("[!] Please close Realme Link on your phone and try again.")
        channel.closeChannel()
        run_runloop(1)
        return

    # Wait for channel to stabilize
    run_runloop(2)

    # Check for connection burst
    burst = delegate.getReceivedData()
    if burst:
        print(f"\n[*] Connection burst: {len(burst)} bytes")
        print(f"    {burst[:80].hex(' ')}{'...' if len(burst) > 80 else ''}")
        # Parse frames in burst
        offset = 0
        while offset < len(burst):
            if burst[offset] != 0xAA:
                offset += 1
                continue
            idx = offset + 1
            flen = 0
            shift = 0
            while idx < len(burst):
                b = burst[idx]
                flen |= (b & 0x7F) << (shift * 7)
                idx += 1
                shift += 1
                if (b & 0x80) == 0:
                    break
            total = idx + flen + 1
            if total > len(burst):
                break
            frame_data = burst[offset:total]
            parsed = parse_response(frame_data)
            if parsed:
                cmd, tid, payload = parsed
                print(f"    frame: cmdId=0x{cmd:04X} tid={tid} len={len(payload)}")
            offset = total
        delegate.clearReceivedData()
    else:
        print("[*] No connection burst")

    # Send command
    if action == "lock":
        frame = build_touch_lock_command(transfer_id=1)
        label = "TOUCH LOCK (all gestures OFF)"
    elif action == "restore":
        frame = build_restore_command(transfer_id=2)
        label = "RESTORE (default gestures)"
    elif action == "status":
        packet = build_packet(0x0108, 1, b"")
        frame = build_oppov1_frame(packet)[0]
        label = "GET KEY FUNCTION (0x0108)"
    elif action == "poll":
        # Send poll sequence
        for cmd_id, cmd_label in [
            (0x0100, "get status"),
            (0x0101, "get device info"),
            (0x0104, "protocol version"),
        ]:
            packet = build_packet(cmd_id, 1, b"")
            frame = build_oppov1_frame(packet)[0]
            print(f"\n[*] Sending POLL {cmd_label} (0x{cmd_id:04X})...")
            print(f"    {frame.hex(' ')}")
            delegate.clearReceivedData()
            data = NSData.dataWithBytes_length_(frame, len(frame))
            try:
                status = channel.writeSync_length_(data, len(frame))
                print(f"    write status: {status}")
            except Exception as e:
                print(f"    write error: {e}")
            run_runloop(3)
            resp = delegate.getReceivedData()
            if resp:
                print(f"    response: {resp.hex(' ')}")
                delegate.clearReceivedData()
            else:
                print(f"    no response")

        # Close and return
        channel.closeChannel()
        run_runloop(1)
        return
    else:
        print(f"[!] Unknown action: {action}")
        channel.closeChannel()
        return

    print(f"\n[*] Sending {label}...")
    print(f"    {frame.hex(' ')}")
    delegate.clearReceivedData()

    data = NSData.dataWithBytes_length_(frame, len(frame))
    try:
        status = channel.writeSync_length_(data, len(frame))
        print(f"    write status: {status}")
    except Exception as e:
        print(f"    write error: {e}")

    # Wait for response
    print("[*] Waiting for response...")
    run_runloop(5)

    resp = delegate.getReceivedData()
    if resp:
        print(f"\n[*] Response ({len(resp)} bytes): {resp.hex(' ')}")
        # Parse all frames
        offset = 0
        while offset < len(resp):
            if resp[offset] != 0xAA:
                offset += 1
                continue
            idx = offset + 1
            flen = 0
            shift = 0
            while idx < len(resp):
                b = resp[idx]
                flen |= (b & 0x7F) << (shift * 7)
                idx += 1
                shift += 1
                if (b & 0x80) == 0:
                    break
            total = idx + flen + 1
            if total > len(resp):
                print(f"    incomplete frame at offset {offset}")
                break
            frame_data = resp[offset:total]
            parsed = parse_response(frame_data)
            if parsed:
                cmd, tid, payload = parsed
                print(f"    frame: cmdId=0x{cmd:04X} tid={tid} payload={payload.hex(' ')}")
                if cmd == 0x8401:
                    status_byte = payload[0] if payload else "?"
                    print(f"    *** TOUCH-LOCK RESPONSE! status={status_byte} ***")
            offset = total
    else:
        print("[*] No response received")

    # Close channel
    print("\n[*] Closing channel...")
    channel.closeChannel()
    run_runloop(1)


if __name__ == "__main__":
    action = sys.argv[1].lower() if len(sys.argv) > 1 else "lock"
    connect_and_send(action)
