package com.phonedoctor.adb

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.interfaces.RSAPublicKey

/**
 * Persistent ADB RSA identity. The private key stays in Android Keystore.
 * The target must still display and accept the ADB authorization prompt.
 */
internal class AdbIdentity private constructor(private val keyPair: KeyPair) {

    fun signToken(token: ByteArray): ByteArray {
        val signer = Signature.getInstance("SHA1withRSA")
        signer.initSign(keyPair.private)
        signer.update(token)
        return signer.sign()
    }

    fun publicKeyPayload(): ByteArray {
        val rsa = keyPair.public as RSAPublicKey
        val modulus = rsa.modulus
        val n = toLittleEndian(modulus, 256)
        val radix = BigInteger.ONE.shiftLeft(2048)
        val rr = toLittleEndian(radix.multiply(radix).mod(modulus), 256)
        val n0inv = modulus.and(BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE))
            .modInverse(BigInteger.ONE.shiftLeft(32)).negate()
            .mod(BigInteger.ONE.shiftLeft(32)).toLong()

        val buffer = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(64)
        for (i in 0 until 64) {
            val word = ((n[i * 4].toLong() and 0xff) or
                ((n[i * 4 + 1].toLong() and 0xff) shl 8) or
                ((n[i * 4 + 2].toLong() and 0xff) shl 16) or
                ((n[i * 4 + 3].toLong() and 0xff) shl 24))
            buffer.putInt(word.toInt())
        }
        buffer.putInt(n0inv.toInt())
        buffer.put(rr)
        buffer.putInt(rsa.publicExponent.toInt())
        val encoded = Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
        return (encoded + " PhoneDoctor@android\u0000").toByteArray(Charsets.US_ASCII)
    }

    private fun toLittleEndian(value: BigInteger, size: Int): ByteArray {
        val big = value.toByteArray()
        val unsigned = if (big.size > size && big[0] == 0.toByte()) big.copyOfRange(1, big.size) else big
        require(unsigned.size <= size) { "RSA integer exceeds expected size" }
        return ByteArray(size).also { out ->
            for (i in unsigned.indices) out[i] = unsigned[unsigned.size - 1 - i]
        }
    }

    companion object {
        private const val ALIAS = "PhoneDoctorAdbRsa2048"

        fun loadOrCreate(): AdbIdentity {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!store.containsAlias(ALIAS)) {
                val generator = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore"
                )
                generator.initialize(
                    KeyGenParameterSpec.Builder(
                        ALIAS,
                        KeyProperties.PURPOSE_SIGN
                    )
                        .setKeySize(2048)
                        .setDigests(KeyProperties.DIGEST_SHA1)
                        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                        .build()
                )
                generator.generateKeyPair()
            }
            val privateKey = store.getKey(ALIAS, null) as java.security.PrivateKey
            val publicKey = store.getCertificate(ALIAS).publicKey
            return AdbIdentity(KeyPair(publicKey, privateKey))
        }
    }
}
