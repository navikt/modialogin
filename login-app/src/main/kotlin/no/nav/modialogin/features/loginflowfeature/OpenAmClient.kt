package no.nav.modialogin.features.loginflowfeature

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.nav.modialogin.common.*
import no.nav.modialogin.common.KtorServer.log
import no.nav.modialogin.common.KtorServer.tjenestekallLogger
import java.net.URL
import kotlin.time.Duration.Companion.seconds

class OpenAmClient {
    @Serializable
    class WellKnownResult(
        @SerialName("jwks_uri") val jwksUrl: String,
        @SerialName("token_endpoint") val tokenEndpoint: String,
        @SerialName("authorization_endpoint") val authorizationEndpoint: String,
        val issuer: String
    )

    @Serializable
    class TokenExchangeResult(
        @SerialName("id_token") val idToken: String,
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
    )

    open class JwksClientConfig(val discoveryUrl: String)
    class TokenExchangeConfig(
        discoveryUrl: String,
        val clientId: String,
        val clientSecret: String,
    ) : JwksClientConfig(discoveryUrl)

    open class JwksClient(config: JwksClientConfig) {
        private val client = HttpClient(Apache) {
            json()
            defaultRequest {
                header(HttpHeaders.CacheControl, "no-cache")
            }
        }

        val wellknown: WellKnownResult by lazy {
            runBlocking {
                KotlinUtils.retry(10, 2.seconds) {
                    log.info("Fetching oidc from ${config.discoveryUrl}")
                    client.get(URL(config.discoveryUrl)).body()
                }
            }
        }
    }

    class TokenExchangeClient(private val config: TokenExchangeConfig) : JwksClient(config) {
        private val authenticatedClient: HttpClient by lazy {
            HttpClient(Apache) {
                logging()
                basicAuth(config.clientId, requireNotNull(config.clientSecret))
                json()
                defaultRequest {
                    header(HttpHeaders.CacheControl, "no-cache")
                }
            }
        }

        suspend fun openAmExchangeAuthCodeForToken(code: String, loginUrl: String): TokenExchangeResult? =
            withContext(Dispatchers.IO) {
                val response = authenticatedClient.post(URL(wellknown.tokenEndpoint)) {
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                set("grant_type", "authorization_code")
                                set("realm", "/")
                                set("redirect_uri", loginUrl)
                                set("code", code)
                            }
                        )
                    )
                }
                if (response.status.isSuccess()) {
                    response.body()
                } else {
                    tjenestekallLogger.error("Could not get token for $code: {}", response.bodyAsText())
                    null
                }
            }

        suspend fun refreshIdToken(refreshToken: String): TokenExchangeResult? =
            withContext(Dispatchers.IO) {
                val response = authenticatedClient.post(URL(wellknown.tokenEndpoint)) {
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                set("grant_type", "refresh_token")
                                set("scope", "openid")
                                set("realm", "/")
                                set("refresh_token", refreshToken)
                            }
                        )
                    )
                }
                if (response.status.isSuccess()) {
                    response.body()
                } else {
                    tjenestekallLogger.error("Could not get token for $refreshToken: {}", response.bodyAsText())
                    null
                }
            }
    }
}
