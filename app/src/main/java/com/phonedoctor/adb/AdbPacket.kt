package com.phonedoctor.adb

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

/**
 * ADB host protocol packet framing (AOSP adbd wire format).
 * This class only encodes/decodes packets; it does not bypass target authorization.
 */
internal data class AdbPacket(
    val command: Int,
    val arg0: Int = 0,
    val arg1: Int = 0,
    val payload: ByteArray = ByteArray(0)
) {
    fun encode(): ByteArray {
        require(payload.size <= MAX_PAYLOAD) { "ADB payload is too large" }
        val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(command)
        header.putInt(arg0)
        header.putInt(arg1)
        header.putInt(payload.size)
        header.putInt(checksum(payload))
        header.putInt(command.inv())
        val result = ByteArray(HEADER_SIZE + payload.size)
        System.arraycopy(header.array(), 0, result, 0, HEADER_SIZE)
        System.arraycopy(payload, 0, result, HEADER_SIZE, payload.size)
        return result
    }

    companion object {
        const val HEADER_SIZE = 24
        const val MAX_PAYLOAD = 1024 * 1024

        fun command(text: String): Int {
            require(text.length == 4) { "ADB command must contain four ASCII characters" }
            return text.toByteArray(Charsets.US_ASCII).let {
                (it[0].toInt() and 0xff) or
                    ((it[1].toInt() and 0xff) shl 8) or
                    ((it[2].toInt() and 0xff) shl 16) or
                    ((it[3].toInt() and 0xff) shl 24)
            }
        }

        fun commandText(value: Int): String = String(
            byteArrayOf(value.toByte(), (value ushr 8).toByte(), (value ushr 16).toByte(), (value ushr 24).toByte()),
            Charsets.US_ASCII
        )

        fun decodeHeader(bytes: ByteArray): AdbPacketHeader {
            require(bytes.size == HEADER_SIZE) { "ADB header must be 24 bytes" }
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val command = b.int
            val arg0 = b.int
            val arg1 = b.int
            val length = b.int
            val checksum = b.int
            val magic = b.int
            require(magic == command.inv()) { "Invalid ADB packet magic" }
            require(length in 0..MAX_PAYLOAD) { "Invalid ADB payload length: $length" }
            return AdbPacketHeader(command, arg0, arg1, length, checksum)
        }

        fun checksum(bytes: ByteArray): Int {
            var sum = 0
            for (byte in bytes) sum += byte.toInt() and 0xff
            return sum
        }

        fun encodeText(text: String): ByteArray = text.toByteArray(Charsets.UTF_8)
    }
}

internal data class AdbPacketHeader(
    val command: Int,
    val arg0: Int,
    val arg1: Int,
    val payloadLength: Int,
    val checksum: Int
) {
    fun validatePayload(payload: ByteArray) {
        require(payload.size == payloadLength) { "ADB payload length mismatch" }
        require(AdbPacket.checksum(payload) == checksum) { "ADB payload checksum mismatch" }
    }
}
