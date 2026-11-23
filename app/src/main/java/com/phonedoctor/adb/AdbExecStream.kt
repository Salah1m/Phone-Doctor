package com.phonedoctor.adb

import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Minimal single-stream ADB service client for exec services (e.g. screencap).
 * Requires a successfully authenticated ADB transport.
 */
internal class AdbExecStream(private val transport: AdbUsbTransport) {
    fun run(service: String, maxOutputBytes: Int = 16 * 1024 * 1024): ByteArray {
        require(service.isNotBlank() && '\u0000' !in service)
        val localId = 1
        transport.writePacket(
            AdbPacket(
                command = AdbPacket.command("OPEN"),
                arg0 = localId,
                arg1 = 0,
                payload = (service + "\u0000").toByteArray(Charsets.UTF_8)
            )
        )

        var remoteId: Int? = null
        val output = ByteArrayOutputStream()
        var opened = false
        try {
            while (true) {
                val packet = transport.readPacket(15_000)
                when (AdbPacket.commandText(packet.command)) {
                    "OKAY" -> {
                        if (packet.arg1 != localId) continue
                        remoteId = packet.arg0
                        opened = true
                    }
                    "WRTE" -> {
                        if (!opened || packet.arg1 != localId || packet.arg0 != remoteId) continue
                        if (output.size() + packet.payload.size > maxOutputBytes) {
                            throw IOException("ADB output exceeded the safety limit")
                        }
                        output.write(packet.payload)
                        transport.writePacket(
                            AdbPacket(AdbPacket.command("OKAY"), localId, remoteId!!)
                        )
                    }
                    "CLSE" -> {
                        if (opened && packet.arg1 == localId && packet.arg0 == remoteId) {
                            return output.toByteArray()
                        }
                        if (!opened) throw IOException("The target rejected the ADB service: $service")
                    }
                    "AUTH" -> throw IOException("ADB authorization was lost during the command")
                    else -> throw IOException("Unexpected ADB stream packet: ${AdbPacket.commandText(packet.command)}")
                }
            }
        } finally {
            if (opened && remoteId != null) {
                try {
                    transport.writePacket(AdbPacket(AdbPacket.command("CLSE"), localId, remoteId))
                } catch (_: Exception) { }
            }
        }
    }

    fun captureScreenPng(): ByteArray {
        val bytes = run("exec:screencap -p")
        if (bytes.size < 8 ||
            bytes[0] != 0x89.toByte() ||
            bytes[1] != 0x50.toByte() ||
            bytes[2] != 0x4e.toByte() ||
            bytes[3] != 0x47.toByte()
        ) throw IOException("The target did not return a valid PNG screenshot")
        return bytes
    }
}
