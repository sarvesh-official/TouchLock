package com.sarvesh.touchlock

/**
 * OPOv1 protocol frame builder for Realme/OPPO/OnePlus earbuds.
 *
 * Frame format (verified on Realme Buds Air 8 and OnePlus Nord Buds 3 Pro):
 *   [0xAA] [len] [0x00] [0x00] [cmdId_lo] [cmdId_hi] [seq] [payloadLen_lo] [payloadLen_hi] [payload]
 *
 * - cmdId and payloadLen are little-endian (matches Gadgetbridge).
 * - len = 7 + payload.size  (everything after 0xAA and the len byte itself)
 * - The buds process commands silently — no response frame is returned on channel 15.
 *
 * Transport: Bluetooth Classic RFCOMM, SDP service "Realme Haydn",
 *   UUID 0000079A-D102-11E1-9B23-00025B00A5A5
 *
 * Gesture values from Gadgetbridge TouchConfigValue.java:
 *   0x00 = OFF, 0x01 = Play/Pause, 0x03 = Voice Assistant (OPPO),
 *   0x04 = Voice Assistant (Realme), 0x05 = Previous, 0x06 = Next,
 *   0x08 = Noise Control (ANC), 0x0B = Volume Up, 0x0C = Volume Down,
 *   0x11 = Game Mode
 */
object OpoProtocol {

    const val OPO_UUID = "0000079A-D102-11E1-9B23-00025B00A5A5"
    const val OPO_UUID_ALT = "00001107-D102-11E1-9B23-00025B00A5A5"

    // Command IDs (little-endian in the frame)
    private const val CMD_TOUCH_CONFIG_SET = 0x0401
    private const val CMD_FIND_DEVICE = 0x0400
    const val CMD_BATTERY_REQ = 0x0106
    private const val CMD_BATTERY_RET = 0x8106

    // Gesture action codes (from Gadgetbridge TouchConfigValue.java)
    const val GESTURE_OFF = 0x00
    const val GESTURE_PLAY_PAUSE = 0x01
    const val GESTURE_VOICE_ASSISTANT = 0x03
    const val GESTURE_VOICE_ASSISTANT_REALME = 0x04
    const val GESTURE_PREVIOUS = 0x05
    const val GESTURE_NEXT = 0x06
    const val GESTURE_NOISE_CONTROL = 0x08
    const val GESTURE_VOLUME_UP = 0x0B
    const val GESTURE_VOLUME_DOWN = 0x0C
    const val GESTURE_GAME_MODE = 0x11

    // Gesture types (from Gadgetbridge TouchConfigType.java)
    const val TYPE_SINGLE_TAP = 0x0101
    const val TYPE_DOUBLE_TAP = 0x0201
    const val TYPE_TRIPLE_TAP = 0x0301
    const val TYPE_LONG_PRESS = 0x0401

    // Sides (from Gadgetbridge TouchConfigSide.java)
    const val SIDE_LEFT = 0x01
    const val SIDE_RIGHT = 0x02

    // All 8 gesture slots: (side, type)
    val ALL_SLOTS = listOf(
        Triple(SIDE_LEFT, TYPE_SINGLE_TAP, "Left - Single Tap"),
        Triple(SIDE_LEFT, TYPE_DOUBLE_TAP, "Left - Double Tap"),
        Triple(SIDE_LEFT, TYPE_TRIPLE_TAP, "Left - Triple Tap"),
        Triple(SIDE_LEFT, TYPE_LONG_PRESS, "Left - Long Press"),
        Triple(SIDE_RIGHT, TYPE_SINGLE_TAP, "Right - Single Tap"),
        Triple(SIDE_RIGHT, TYPE_DOUBLE_TAP, "Right - Double Tap"),
        Triple(SIDE_RIGHT, TYPE_TRIPLE_TAP, "Right - Triple Tap"),
        Triple(SIDE_RIGHT, TYPE_LONG_PRESS, "Right - Long Press"),
    )

    // Available gesture values for the settings screen
    data class GestureOption(val code: Int, val label: String)
    val GESTURE_OPTIONS = listOf(
        GestureOption(GESTURE_OFF, "Off"),
        GestureOption(GESTURE_PLAY_PAUSE, "Play / Pause"),
        GestureOption(GESTURE_NEXT, "Next Track"),
        GestureOption(GESTURE_PREVIOUS, "Previous Track"),
        GestureOption(GESTURE_NOISE_CONTROL, "Noise Control (ANC)"),
        GestureOption(GESTURE_VOICE_ASSISTANT, "Voice Assistant"),
        GestureOption(GESTURE_VOLUME_UP, "Volume Up"),
        GestureOption(GESTURE_VOLUME_DOWN, "Volume Down"),
        GestureOption(GESTURE_GAME_MODE, "Game Mode"),
    )

    // Supported device name patterns for auto-detection
    val SUPPORTED_DEVICE_PATTERNS = listOf(
        "realme buds",
        "buds air",
        "nord buds",
        "oneplus",
        "enco",
    )

    private var sequenceNumber: Byte = 1

    /**
     * Build a list of single-slot touch-lock frames: all 8 gesture slots set to OFF.
     */
    fun buildTouchLockFrames(): List<ByteArray> =
        ALL_SLOTS.map { (side, type, _) -> buildSingleSlotFrame(side, type, GESTURE_OFF) }

    /**
     * Build a list of single-slot TOUCH_CONFIG_SET frames reflecting the lock state.
     * Gadgetbridge sends one slot at a time — some devices (Air 5 Pro) ignore
     * multi-slot commands. Returns one frame per slot (8 total).
     *
     * @param leftLocked  If true, left bud gestures are all OFF
     * @param rightLocked If true, right bud gestures are all OFF
     * @param gestureValues Saved gesture values (8 entries, one per slot in ALL_SLOTS order)
     */
    fun buildGestureFrames(
        leftLocked: Boolean,
        rightLocked: Boolean,
        gestureValues: List<Int>,
    ): List<ByteArray> {
        return ALL_SLOTS.mapIndexed { i, (side, type, _) ->
            val locked = when (side) {
                SIDE_LEFT -> leftLocked
                else -> rightLocked
            }
            val value = if (locked) GESTURE_OFF else gestureValues.getOrElse(i) { GESTURE_OFF }
            buildSingleSlotFrame(side, type, value)
        }
    }

    /**
     * Build a list of single-slot restore frames from saved gesture config.
     */
    fun buildRestoreFrames(gestureValues: List<Int>): List<ByteArray> {
        return ALL_SLOTS.mapIndexed { i, (side, type, _) ->
            buildSingleSlotFrame(side, type, gestureValues.getOrElse(i) { GESTURE_OFF })
        }
    }

    /**
     * Build a single-slot TOUCH_CONFIG_SET frame (count=1).
     * This matches Gadgetbridge's encodeSendConfiguration format.
     */
    private fun buildSingleSlotFrame(side: Int, type: Int, gesture: Int): ByteArray {
        val payload = ByteArray(5)
        payload[0] = 0x01 // count = 1
        payload[1] = side.toByte()
        payload[2] = (type and 0xFF).toByte()
        payload[3] = ((type shr 8) and 0xFF).toByte()
        payload[4] = gesture.toByte()
        return buildFrame(CMD_TOUCH_CONFIG_SET, payload)
    }

    /**
     * Build a find-device frame (makes the buds beep).
     */
    fun buildFindDeviceFrame(): ByteArray = buildFrame(CMD_FIND_DEVICE, byteArrayOf(0x01))

    /**
     * Build a find-device stop frame (stops the beep).
     */
    fun buildFindDeviceStopFrame(): ByteArray = buildFrame(CMD_FIND_DEVICE, byteArrayOf(0x00))

    /**
     * Build a prompt sound volume set frame.
     * Volume is 1-10 (10 = max). Controls beep/system prompt loudness.
     * Cmd: 0x2704 (group=0x27, cmd=0x04), payload=[volume]
     */
    fun buildPromptVolumeFrame(volume: Int): ByteArray =
        buildFrame(0x2704, byteArrayOf(volume.coerceIn(1, 10).toByte()))

    /**
     * Build a battery request frame.
     */
    fun buildBatteryRequestFrame(): ByteArray = buildFrame(CMD_BATTERY_REQ, byteArrayOf())

    /**
     * Parse a BATTERY_RET (0x8106) response frame.
     * Payload format (from Gadgetbridge OppoHeadphonesProtocol.java):
     *   [status(1)] [numBatteries(1)] [index(1) level(1)] × numBatteries
     * index: 1=left, 2=right, 3=case
     * level: 0x7f mask (top bit may indicate charging)
     * Returns (left, right, case) percentages, or null if invalid.
     */
    fun parseBatteryResponse(data: ByteArray): Triple<Int, Int, Int>? {
        if (data.size < 9) return null
        if (data[0] != 0xAA.toByte()) return null
        val cmdId = (data[4].toInt() and 0xFF) or ((data[5].toInt() and 0xFF) shl 8)
        if (cmdId != CMD_BATTERY_RET) return null
        val payloadLen = (data[7].toInt() and 0xFF) or ((data[8].toInt() and 0xFF) shl 8)
        if (data.size < 9 + payloadLen) return null

        val payload = data.copyOfRange(9, 9 + payloadLen)
        if (payload.size < 2 || payload[0] != 0.toByte()) return null

        val numBatteries = payload[1].toInt() and 0xFF
        var left = -1
        var right = -1
        var case = -1

        var i = 2
        while (i + 1 < payload.size) {
            val idx = payload[i].toInt() and 0xFF
            if (idx == 0xFF) { i += 2; continue }
            val level = payload[i + 1].toInt() and 0x7F
            // Case battery reads 0 when buds are out of the case — treat as unknown.
            // Matches Gadgetbridge's parseBattery logic.
            if (idx == 3 && level == 0) { i += 2; continue }
            when (idx) {
                1 -> left = level
                2 -> right = level
                3 -> case = level
            }
            i += 2
        }
        return Triple(left, right, case)
    }

    /**
     * Build a complete OPOv1 frame.
     */
    private fun buildFrame(cmdId: Int, payload: ByteArray): ByteArray {
        val headerSize = 9
        val totalSize = headerSize + payload.size
        val frame = ByteArray(totalSize)

        frame[0] = 0xAA.toByte()
        frame[1] = (totalSize - 2).toByte()
        frame[2] = 0x00
        frame[3] = 0x00
        frame[4] = (cmdId and 0xFF).toByte()
        frame[5] = ((cmdId shr 8) and 0xFF).toByte()
        frame[6] = sequenceNumber++
        frame[7] = (payload.size and 0xFF).toByte()
        frame[8] = ((payload.size shr 8) and 0xFF).toByte()
        System.arraycopy(payload, 0, frame, 9, payload.size)

        return frame
    }

    fun toHex(bytes: ByteArray): String =
        bytes.joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }
}
