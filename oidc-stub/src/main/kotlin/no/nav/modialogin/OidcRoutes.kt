package no.nav.modialogin

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import com.nimbusds.jwt.SignedJWT
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

val TOKEN_LIFESPAN = 10 * 60 * 60 * 1000
@Serializable
class WellKnownResult(
    @SerialName("jwks_uri") val url: String,
    @SerialName("token_endpoint") val tokenEndpoint: String,
    @SerialName("authorization_endpoint") val authorizationEndpoint: String,
    val issuer: String
)

@Serializable
class TokenExchangeResult(
    @SerialName("id_token") val idToken: String,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null
)

fun Application.oidc(
    route: String,
    issuer: String,
    outsideDocker: Boolean = false,
    supportOnBehalfOf: Boolean = true,
) {
    val key = RSAKeyGenerator(2048)
        .keyUse(KeyUse.SIGNATURE)
        .keyID(UUID.randomUUID().toString())
        .generate()
    val signer = RSASSASigner(key)
    val jwks = JWKSet(key)
    val baseUrl = if (outsideDocker) "http://localhost:8080" else "http://oidc-stub:8080"
    var lastNonce: String? = null

    routing {
        route(route) {
            route(".well-known") {
                get("openid-configuration") {
                    call.respond(
                        WellKnownResult(
                            url = "$baseUrl/$route/.well-known/jwks.json",
                            tokenEndpoint = "$baseUrl/$route/oauth/token",
                            authorizationEndpoint = "http://localhost:8080/$route/authorize",
                            issuer = issuer
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
                val nonce = call.parameters["nonce"]
                val code = UUID.randomUUID().toString()
                val queryParamDelimiter = if (redirectUri.contains("?")) "&" else "?"
                lastNonce = nonce

                call.respondRedirect(
                    permanent = false,
                    url = "$redirectUri${queryParamDelimiter}code=$code&state=$state"
                )
            }

            post("oauth/token") {
                val params = try { call.receiveParameters() } catch (e: Throwable) { null }
                if (supportOnBehalfOf && params?.get("requested_token_use") == "on_behalf_of") {
                    val claimset = JWTClaimsSet
                        .Builder()
                        .expirationTime(Date(System.currentTimeMillis() + 600_000))
                        .build()
                    val accessToken = PlainJWT(claimset).serialize()

                    call.respondText(
                        "{ \"token_type\": \"bearer\", \"access_token\": \"$accessToken\" }",
                        ContentType.Application.Json,
                        HttpStatusCode.OK
                    )
                } else {
                    val nonce = lastNonce
                    lastNonce = null
                    call.respond(
                        TokenExchangeResult(
                            idToken = SignedJWT(
                                JWSHeader.Builder(JWSAlgorithm.RS256)
                                    .keyID(key.keyID)
                                    .build(),
                                JWTClaimsSet.Builder()
                                    .issuer(issuer)
                                    .audience("foo")
                                    .subject("Z999999")
                                    .issueTime(Date())
                                    .expirationTime(Date(System.currentTimeMillis() + TOKEN_LIFESPAN))
                                    .claim("nonce", nonce)
                                    .build()
                            )
                                .apply { sign(signer) }
                                .serialize(),
                            accessToken = SignedJWT(
                                JWSHeader.Builder(JWSAlgorithm.RS256)
                                    .keyID(key.keyID)
                                    .build(),
                                JWTClaimsSet.Builder()
                                    .issuer(issuer)
                                    .audience("foo")
                                    .subject("Z999999")
                                    .issueTime(Date())
                                    .expirationTime(Date(System.currentTimeMillis() + TOKEN_LIFESPAN))
                                    .claim("nonce", nonce)
                                    .claim("scope", "openid offline_access")
                                    .build()
                            )
                                .apply { sign(signer) }
                                .serialize(),
                            refreshToken = "refresh_token"
                        )
                    )
                }
            }
        }
    }
}
