#!/usr/bin/env python3
"""
Builds the exact wire bytes for the Realme Buds touch-lock command,
replicating the OPPOv1Wrapper + Packet encoding from the Realme Link APK.

Verified against decompiled source:
  - Packet.java method a()
  - OPPOv1Wrapper.java method m()
  - SetCommandManager.java method s() (setKeyFunction, cmd 0x0401)
  - ParamsConverter.java method A() (function=0 means OFF)
"""

import struct


def build_packet(command_id: int, transfer_id: int, payload: bytes) -> bytes:
    """
    Build the inner packet for an OPOv1 frame.

    command_id is the 16-bit command value (e.g. 0x0401 for set key function).
    Internally it splits into (group=low byte, cmd=high byte):
      0x0401 -> group=0x01, cmd=0x04

    Wire format (matches Swastik36/OPPO-Earbuds OppoFrame.to_bytes):
      [group(1)] [cmd(1)] [seq(1)] [payload_len LE(2)] [payload]
    """
    group = command_id & 0xFF
    cmd = (command_id >> 8) & 0xFF
    return (
        struct.pack("BB", group, cmd)
        + struct.pack("B", transfer_id)
        + struct.pack("<H", len(payload))
        + payload
    )


def leb128_encode(value: int) -> bytes:
    """Replicate OPPOv1Wrapper.o() — LEB128 encode a length."""
    if value == 0:
        return b"\x00"
    result = []
    while value > 0:
        byte = value & 0x7F
        value >>= 7
        if value > 0:
            byte |= 0x80
        result.append(byte)
    return bytes(result)


def build_oppov1_frame(packet_bytes: bytes, mtu: int = 2000) -> list[bytes]:
    """
    Replicate OPPOv1Wrapper.m() — split into frames.
    Returns a list of frame byte-strings (usually just one).
    """
    frames = []
    total_len = len(packet_bytes)
    offset = 0
    frame_seq = 0

    while total_len > 0:
        # j(i6, length, mtu) — compute length-field size
        # For our small payloads, length is always < 127, so length_size = 1
        length_size = 1

        # payload_size_and_fsn_size = (mtu - length_size - 1 - 2) - i5
        # i5 is 0 (no prefix)
        max_payload = mtu - length_size - 1 - 2

        if frame_seq == 0:
            # First frame
            if total_len <= max_payload:
                # Single frame, no FSN
                has_fsn = False
                ctrl_fsn = 0  # single frame
                chunk_size = total_len
            else:
                # First of multi-frame
                has_fsn = True
                ctrl_fsn = 1  # first frame
                chunk_size = max_payload - 1  # -1 for FSN byte
        else:
            # Subsequent frames
            has_fsn = True
            if total_len <= max_payload - 1:
                ctrl_fsn = 3  # last frame
                chunk_size = total_len
            else:
                ctrl_fsn = 2  # middle frame
                chunk_size = max_payload - 1

        chunk = packet_bytes[offset : offset + chunk_size]

        # Frame payload size = chunk_size + 2 (ctrl + reserved) + (1 if FSN)
        frame_payload_size = chunk_size + 2 + (1 if has_fsn else 0)

        # Build frame: [0xAA] [leb128(frame_payload_size)] [ctrl] [0x00] [fsn?] [chunk]
        frame = b"\xAA"
        frame += leb128_encode(frame_payload_size)
        frame += struct.pack("B", ctrl_fsn)
        frame += b"\x00"  # reserved
        if has_fsn:
            frame += struct.pack("B", frame_seq)
        frame += chunk

        frames.append(frame)
        offset += chunk_size
        total_len -= chunk_size
        frame_seq += 1

    return frames


def build_touch_lock_payload(entries: list[tuple[int, int, int, int]]) -> bytes:
    """
    Build the setKeyFunction payload (cmd 0x0401).
    Each entry is (device_type, button, action, function).
    """
    payload = struct.pack("B", len(entries))
    for dev, btn, act, func in entries:
        payload += struct.pack("BBBB", dev, btn, act, func)
    return payload


def build_touch_lock_command(transfer_id: int = 1) -> bytes:
    """
    Build the complete touch-lock command (all gestures OFF).
    Returns the single OPPOv1Wrapper frame to write to the RFCOMM socket.
    """
    # All common touch gestures on both buds, set to OFF (function=0)
    entries = [
        (1, 1, 1, 0),  # left bud,  touch, single tap, OFF
        (1, 1, 2, 0),  # left bud,  touch, double tap, OFF
        (1, 1, 3, 0),  # left bud,  touch, triple tap, OFF
        (1, 1, 4, 0),  # left bud,  touch, long press, OFF
        (2, 1, 1, 0),  # right bud, touch, single tap, OFF
        (2, 1, 2, 0),  # right bud, touch, double tap, OFF
        (2, 1, 3, 0),  # right bud, touch, triple tap, OFF
        (2, 1, 4, 0),  # right bud, touch, long press, OFF
    ]
    payload = build_touch_lock_payload(entries)
    packet = build_packet(0x0401, transfer_id, payload)
    frames = build_oppov1_frame(packet)
    return frames[0]  # single frame for this small payload


def build_restore_command(transfer_id: int = 2) -> bytes:
    """
    Build a restore command that re-enables default touch gestures.
    This is a guess — the actual default values may differ per device.
    """
    entries = [
        (1, 1, 1, 1),  # left bud,  touch, single tap, play/pause
        (1, 1, 2, 6),  # left bud,  touch, double tap, next track
        (1, 1, 3, 5),  # left bud,  touch, triple tap, prev track
        (1, 1, 4, 3),  # left bud,  touch, long press, ANC toggle
        (2, 1, 1, 1),  # right bud, touch, single tap, play/pause
        (2, 1, 2, 6),  # right bud, touch, double tap, next track
        (2, 1, 3, 5),  # right bud, touch, triple tap, prev track
        (2, 1, 4, 3),  # right bud, touch, long press, ANC toggle
    ]
    payload = build_touch_lock_payload(entries)
    packet = build_packet(0x0401, transfer_id, payload)
    frames = build_oppov1_frame(packet)
    return frames[0]


def parse_response(frame: bytes) -> tuple[int, int, bytes] | None:
    """
    Parse an OPOv1 frame.
    Returns (command_id, transfer_id, payload) where command_id = (cmd << 8) | group.
    Returns None if invalid.
    """
    if len(frame) < 7 or frame[0] != 0xAA:
        return None
    # Parse LEB128 length
    idx = 1
    length = 0
    shift = 0
    while idx < len(frame):
        b = frame[idx]
        length |= (b & 0x7F) << (shift * 7)
        idx += 1
        shift += 1
        if (b & 0x80) == 0:
            break
    # ctrl byte (0 for single-frame) and reserved 0x00
    ctrl = frame[idx]
    idx += 1
    idx += 1  # reserved
    # FSN if multi-frame (ctrl & 0x03 != 0)
    if ctrl & 0x03 != 0:
        idx += 1  # skip FSN
    # Inner packet: [group(1)] [cmd(1)] [seq(1)] [payload_len LE(2)] [payload]
    packet_bytes = frame[idx:]
    if len(packet_bytes) < 5:
        return None
    group = packet_bytes[0]
    cmd = packet_bytes[1]
    transfer_id = packet_bytes[2]
    data_len = struct.unpack("<H", packet_bytes[3:5])[0]
    payload = packet_bytes[5 : 5 + data_len]
    command_id = (cmd << 8) | group
    return (command_id, transfer_id, payload)


if __name__ == "__main__":
    print("=== Touch Lock Command (all gestures OFF) ===")
    frame = build_touch_lock_command()
    print(f"Length: {len(frame)} bytes")
    print(f"Hex: {frame.hex(' ')}")
    print()

    print("=== Restore Command (default gestures) ===")
    restore = build_restore_command()
    print(f"Length: {len(restore)} bytes")
    print(f"Hex: {restore.hex(' ')}")
    print()

    # Verify against the expected bytes (correct OPOv1 format: group,cmd,seq,payloadLen LE)
    expected = bytes.fromhex(
        "AA 28 00 00 01 04 01 21 00 08 "
        "01 01 01 00 01 01 02 00 01 01 03 00 01 01 04 00 "
        "02 01 01 00 02 01 02 00 02 01 03 00 02 01 04 00".replace(" ", "")
    )
    print("=== Verification ===")
    print(f"Matches expected: {frame == expected}")
    if frame != expected:
        print(f"Expected: {expected.hex(' ')}")
        print(f"Got:      {frame.hex(' ')}")

    # Round-trip parse test
    parsed = parse_response(frame)
    if parsed:
        cmd_id, tid, payload = parsed
        print(f"Round-trip: cmdId=0x{cmd_id:04X} tid={tid} payloadLen={len(payload)}")
        assert cmd_id == 0x0401, f"cmdId mismatch: 0x{cmd_id:04X}"
        assert tid == 1
        assert len(payload) == 33
        print("Round-trip OK")
