package no.nav.modialogin.common

class Crypter(secret: String) {
    private val password = secret.substring(secret.length / 2)
    private val salt = secret.removePrefix(password)
    private val key = AES.generateKey(password, salt)


    fun encrypt(plaintext: String): String = encryptSafe(plaintext).getOrThrow()
    fun decrypt(ciphertext: String): String = decryptSafe(ciphertext).getOrThrow()

    fun encryptSafe(plaintext: String): Result<String> =
        runCatching { plaintext.toByteArray() }
            .mapCatching { AES.encrypt(it, key) }
            .mapCatching { Encoding.encode(it) }

    fun decryptSafe(ciphertext: String): Result<String> =
        runCatching { Encoding.decode(ciphertext) }
            .mapCatching { AES.decrypt(it, key) }
            .mapCatching { String(it) }
}