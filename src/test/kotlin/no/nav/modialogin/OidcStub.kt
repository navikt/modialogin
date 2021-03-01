package no.nav.modialogin

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.modialogin.utils.OidcClient
import java.util.UUID

fun main() {
    val rsaKey: RSAKey = RSAKeyGenerator(2048)
        .keyUse(KeyUse.SIGNATURE) // indicate the intended use of the key
        .keyID(UUID.randomUUID().toString()) // give the key a unique ID
        .generate()

    val signer = RSASSASigner(rsaKey)
    val jwks = JWKSet(rsaKey)

    embeddedServer(Netty, 8081) {
        install(ContentNegotiation) {
            json()
        }

        routing {
            route(".well-known") {
                get("openid-configuration") {
                    call.respond(
                        OidcClient.Config(
                            jwksUrl = "http://localhost:8081/.well-known/jwks.json",
                            tokenEndpoint = "http://localhost:8081/oauth/token",
                            authorizationEndpoint = "http://localhost:8081/authorize",
                            issuer = "stub"
                        )
                    )
                }
                get("jwks.json") {
                    call.respond(jwks.toJSONObject())
                }
            }
            get("authorize") {
                val redirectUri = requireNotNull(call.parameters["redirect_uri"])
                val state = requireNotNull(call.parameters["state"])

                val code = UUID.randomUUID().toString()
                call.respondRedirect(
                    permanent = false,
                    url = "$redirectUri?code=$code&state=$state"
                )
            }
            post("oauth/token") {
                call.respond(
                    OidcClient.TokenExchangeResult(
                        idToken = SignedJWT(
                            JWSHeader.Builder(JWSAlgorithm.RS256)
                                .keyID(rsaKey.keyID)
                                .build(),
                            JWTClaimsSet.Builder().audience("modia").subject("Z999999").build()
                        )
                            .apply { sign(signer) }
                            .serialize(),
                        accessToken = "rnadom acces",
                        refreshToken = null
                    )
                )
            }
        }
    }.start(wait = true)
}
