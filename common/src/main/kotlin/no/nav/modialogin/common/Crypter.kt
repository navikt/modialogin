package no.nav.modialogin.common

class Crypter(secret: String) {
    private val password = secret.substring(secret.length / 2)
    private val salt = secret.removePrefix(password)
    private val key = AES.generateKey(password, salt)


    fun encrypt(plaintext: String): String = Encoding.encode(
        AES.encrypt(plaintext.toByteArray(), key)
    )

    fun decrypt(ciphertext: String): String = String(
        AES.decrypt(
            Encoding.decode(ciphertext),
            key
        )
    )
}

fun main() {
    val secret = "YprQ6WgxcBwcOS+jp4Aty4OnJzxzkAO5ijrhlB4DkAc="
    val message = "this is some content"
    val crypter = Crypter(secret)

    val encrypted = crypter.encrypt(message).also(::println)
    crypter.decrypt(encrypted).also(::println)
}