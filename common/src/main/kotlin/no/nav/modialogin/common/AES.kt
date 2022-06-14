package no.nav.modialogin.common

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object AES {
    private val random = SecureRandom()
    private val algorithm = "AES/GCM/NoPadding"
    private val GCM_IV_LENGTH = 16

    fun generateKey(password: String, salt: String): SecretKey {
        val keyfactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyspec = PBEKeySpec(password.toCharArray(), salt.toByteArray(), 65536, 256)
        return SecretKeySpec(keyfactory.generateSecret(keyspec).encoded, "AES")
    }

    fun encrypt(plaintext: ByteArray, key: SecretKey): ByteArray {
        val iv = generateIv()
        val ciphertext = Cipher
            .getInstance(algorithm)
            .apply {
                init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv.iv))
            }
            .run {
                doFinal(plaintext)
            }
        return ByteBuffer.allocate(iv.iv.size + ciphertext.size)
            .put(iv.iv)
            .put(ciphertext)
            .array()
    }

    fun decrypt(ciphertext: ByteArray, key: SecretKey): ByteArray {
        return Cipher
            .getInstance(algorithm)
            .apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, ciphertext, 0, GCM_IV_LENGTH))
            }
            .run {
                doFinal(ciphertext, GCM_IV_LENGTH, ciphertext.size - GCM_IV_LENGTH)
            }
    }

    private fun generateIv(): IvParameterSpec {
        val bytes = ByteArray(GCM_IV_LENGTH)
        random.nextBytes(bytes)
        return IvParameterSpec(bytes)
    }
}