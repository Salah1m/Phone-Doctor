package com.phonedoctor.adb

import java.io.Closeable
import java.io.IOException

/**
 * Performs the initial ADB CNXN/AUTH exchange over an already-open USB ADB
 * transport. It never approves the target automatically: RSA public-key
 * authorization must be accepted on the target device.
 */
internal class AdbHandshake(
    private val transport: AdbUsbTransport,
    private val identity: AdbIdentity
) : Closeable {

    data class Result(val authorized: Boolean, val message: String)

    fun connect(): Result {
        transport.writePacket(
            AdbPacket(
                command = AdbPacket.command("CNXN"),
                arg0 = ADB_VERSION,
                arg1 = MAX_DATA,
                payload = AdbPacket.encodeText("host::features=shell_v2,cmd\u0000")
            )
        )

        var signatureSent = false
        var publicKeySent = false
        repeat(6) {
            val packet = transport.readPacket(10_000)
            when (AdbPacket.commandText(packet.command)) {
                "CNXN" -> return Result(true, "تمت مصادقة ADB والاتصال بالجهاز")
                "AUTH" -> {
                    when (packet.arg0) {
                        AUTH_TOKEN -> {
                            if (!signatureSent) {
                                transport.writePacket(
                                    AdbPacket(
                                        command = AdbPacket.command("AUTH"),
                                        arg0 = AUTH_SIGNATURE,
                                        payload = identity.signToken(packet.payload)
                                    )
                                )
                                signatureSent = true
                            } else if (!publicKeySent) {
                                transport.writePacket(
                                    AdbPacket(
                                        command = AdbPacket.command("AUTH"),
                                        arg0 = AUTH_RSAPUBLICKEY,
                                        payload = identity.publicKeyPayload()
                                    )
                                )
                                publicKeySent = true
                            } else {
                                return Result(
                                    false,
                                    "الهاتف لم يقبل مفتاح ADB بعد. افتح الهاتف المستهدف ووافق على نافذة السماح إذا كانت الشاشة تعمل."
                                )
                            }
                        }
                        else -> throw IOException("نوع طلب مصادقة ADB غير معروف")
                    }
                }
                "STLS" -> throw IOException("الجهاز طلب تفاوض TLS؛ هذا المسار غير مدعوم في هذه النسخة")
                else -> throw IOException("رد ADB غير متوقع: ${AdbPacket.commandText(packet.command)}")
            }
        }
        return Result(
            false,
            if (publicKeySent) "أُرسل مفتاح ADB؛ يلزم قبول التفويض على الهاتف المستهدف ثم إعادة المحاولة."
            else "انتهت مهلة مصادقة ADB."
        )
    }

    override fun close() = transport.close()

    companion object {
        private const val ADB_VERSION = 0x01000000
        private const val MAX_DATA = 256 * 1024
        private const val AUTH_TOKEN = 1
        private const val AUTH_SIGNATURE = 2
        private const val AUTH_RSAPUBLICKEY = 3
    }
}
