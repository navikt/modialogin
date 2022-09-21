package no.nav.modialogin.features.authfeature

import com.auth0.jwk.Jwk
import com.auth0.jwt.algorithms.Algorithm
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey

fun Jwk.makeAlgorithm(): Algorithm = when(algorithm) {
    "RS256" -> Algorithm.RSA256(publicKey as RSAPublicKey, null)
    "RS384" -> Algorithm.RSA384(publicKey as RSAPublicKey, null)
    "RS512" -> Algorithm.RSA512(publicKey as RSAPublicKey, null)
    "ES256" -> Algorithm.ECDSA256(publicKey as ECPublicKey, null)
    "ES384" -> Algorithm.ECDSA384(publicKey as ECPublicKey, null)
    "ES512" -> Algorithm.ECDSA512(publicKey as ECPublicKey, null)
    null -> Algorithm.RSA256(publicKey as RSAPublicKey, null)
    else -> error("Unsupported algorithm $algorithm")
}