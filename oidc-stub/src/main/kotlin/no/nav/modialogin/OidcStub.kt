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
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
class JWKConfig(
    @SerialName("jwks_uri") val url: String,
    @SerialName("token_endpoint") val tokenEndpoint: String,
    @SerialName("authorization_endpoint") val authorizationEndpoint: String,
    val issuer: String
)

@Serializable
class TokenExchangeResult(
    @SerialName("id_token") val idToken: String,
    @SerialName("access_token") val accessToken: String?,
    @SerialName("refresh_token") val refreshToken: String?
)
val TOKEN_LIFESPAN = 10 * 60 * 1000
var lastNonce: String? = null

fun main() {
    val usingDockerCompose = System.getenv("DOCKER_COMPOSE").toBoolean()
    val rsaKey: RSAKey = RSAKeyGenerator(2048)
        .keyUse(KeyUse.SIGNATURE) // indicate the intended use of the key
        .keyID(UUID.randomUUID().toString()) // give the key a unique ID
        .generate()

    val signer = RSASSASigner(rsaKey)
    val jwks = JWKSet(rsaKey)

    embeddedServer(Netty, 8080) {
        install(ContentNegotiation) {
            json()
        }

        routing {
            route(".well-known") {
                get("openid-configuration") {
                    if (usingDockerCompose) {
                        call.respond(
                            JWKConfig(
                                url = "http://oidc-stub:8080/.well-known/jwks.json",
                                tokenEndpoint = "http://oidc-stub:8080/oauth/token",
                                authorizationEndpoint = "http://localhost:8080/authorize",
                                issuer = "stub"
                            )
                        )
                    } else {
                        call.respond(
                            JWKConfig(
                                url = "http://localhost:8080/.well-known/jwks.json",
                                tokenEndpoint = "http://localhost:8080/oauth/token",
                                authorizationEndpoint = "http://localhost:8080/authorize",
                                issuer = "stub"
                            )
                        )
                    }
                }
                get("jwks.json") {
                    call.respond(jwks.toJSONObject())
                }
            }
            get("authorize") {
                val redirectUri = requireNotNull(call.parameters["redirect_uri"])
                val state = requireNotNull(call.parameters["state"])
                val nonce = call.parameters["nonce"]
                val code = UUID.randomUUID().toString()
                lastNonce = nonce

                call.respondRedirect(
                    permanent = false,
                    url = "$redirectUri?code=$code&state=$state"
                )
            }
            post("oauth/token") {
                val nonce = lastNonce
                lastNonce = null
                call.respond(
                    TokenExchangeResult(
                        idToken = SignedJWT(
                            JWSHeader.Builder(JWSAlgorithm.RS256)
                                .keyID(rsaKey.keyID)
                                .build(),
                            JWTClaimsSet.Builder()
                                .issuer("stub")
                                .audience("foo")
                                .subject("Z999999")
                                .issueTime(Date())
                                .expirationTime(Date(System.currentTimeMillis() + TOKEN_LIFESPAN))
                                .claim("nonce", nonce)
                                .build()
                        )
                            .apply { sign(signer) }
                            .serialize(),
                        accessToken = "random acces",
                        refreshToken = "refresh_token"
                    )
                )
            }
        }
    }.start(wait = true)
}
