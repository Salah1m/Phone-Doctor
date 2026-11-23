package com.phonedoctor.adb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import java.io.Closeable
import java.io.IOException

/**
 * Raw USB bulk transport for the standard Android ADB interface.
 * It deliberately refuses non-ADB interfaces such as PTP/MTP.
 */
internal class AdbUsbTransport private constructor(
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    private val input: UsbEndpoint,
    private val output: UsbEndpoint
) : Closeable {

    fun writePacket(packet: AdbPacket, timeoutMs: Int = DEFAULT_TIMEOUT_MS) {
        writeFully(packet.encode(), timeoutMs)
    }

    fun readPacket(timeoutMs: Int = DEFAULT_TIMEOUT_MS): AdbPacket {
        val headerBytes = readFully(AdbPacket.HEADER_SIZE, timeoutMs)
        val header = AdbPacket.decodeHeader(headerBytes)
        val payload = if (header.payloadLength == 0) {
            ByteArray(0)
        } else {
            readFully(header.payloadLength, timeoutMs)
        }
        header.validatePayload(payload)
        return AdbPacket(header.command, header.arg0, header.arg1, payload)
    }

    private fun writeFully(bytes: ByteArray, timeoutMs: Int) {
        var offset = 0
        while (offset < bytes.size) {
            val count = connection.bulkTransfer(
                output, bytes, offset, bytes.size - offset, timeoutMs
            )
            if (count <= 0) throw IOException("ADB USB write failed at byte $offset")
            offset += count
        }
    }

    private fun readFully(length: Int, timeoutMs: Int): ByteArray {
        val result = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val chunk = ByteArray(length - offset)
            val count = connection.bulkTransfer(input, chunk, chunk.size, timeoutMs)
            if (count <= 0) throw IOException("ADB USB read timed out at byte $offset")
            System.arraycopy(chunk, 0, result, offset, count)
            offset += count
        }
        return result
    }

    override fun close() {
        try {
            connection.releaseInterface(usbInterface)
        } finally {
            connection.close()
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 5000

        fun hasStandardAdbInterface(device: UsbDevice): Boolean =
            findAdbInterface(device) != null

        fun open(manager: UsbManager, device: UsbDevice): AdbUsbTransport {
            if (!manager.hasPermission(device)) {
                throw SecurityException("USB permission has not been granted for this device")
            }

            val adbInterface = findAdbInterface(device)
                ?: throw IOException("The connected phone does not expose the standard ADB USB interface")

            var bulkIn: UsbEndpoint? = null
            var bulkOut: UsbEndpoint? = null
            for (index in 0 until adbInterface.endpointCount) {
                val endpoint = adbInterface.getEndpoint(index)
                if (endpoint.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                if (endpoint.direction == UsbConstants.USB_DIR_IN) bulkIn = endpoint
                if (endpoint.direction == UsbConstants.USB_DIR_OUT) bulkOut = endpoint
            }

            val input = bulkIn ?: throw IOException("ADB bulk-IN endpoint is missing")
            val output = bulkOut ?: throw IOException("ADB bulk-OUT endpoint is missing")
            val connection = manager.openDevice(device)
                ?: throw IOException("Android could not open the USB device")

            if (!connection.claimInterface(adbInterface, true)) {
                connection.close()
                throw IOException("Could not claim the ADB USB interface")
            }
            return AdbUsbTransport(connection, adbInterface, input, output)
        }

        private fun findAdbInterface(device: UsbDevice): UsbInterface? {
            for (index in 0 until device.interfaceCount) {
                val candidate = device.getInterface(index)
                if (candidate.interfaceClass == 0xFF &&
                    candidate.interfaceSubclass == 0x42 &&
                    candidate.interfaceProtocol == 0x01
                ) return candidate
            }
            return null
        }
    }
}
