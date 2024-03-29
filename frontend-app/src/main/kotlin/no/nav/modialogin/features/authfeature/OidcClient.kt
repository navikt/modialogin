package no.nav.modialogin.features.authfeature

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.nav.modialogin.Logging.log
import no.nav.modialogin.utils.*
import no.nav.modialogin.utils.KotlinUtils.callId
import java.net.URL
import kotlin.time.Duration.Companion.seconds

class OidcClient(val config: Config) {
    class Config(
        val clientId: String,
        val clientSecret: String,
        val wellKnownUrl: String
    )
    @Serializable
    class TokenExchangeResult(
        @SerialName("id_token") val idToken: String,
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String,
    )
    @Serializable
    class WellKnownResult(
        @SerialName("jwks_uri") val jwksUrl: String,
        @SerialName("token_endpoint") val tokenEndpoint: String,
        @SerialName("authorization_endpoint") val authorizationEndpoint: String,
        val issuer: String
    )

    val httpClient: HttpClient by lazy {
        HttpClient(Apache) {
            useProxy()
            logging()
            basicAuth(config.clientId, requireNotNull(config.clientSecret))
            json()
            defaultRequest {
                header(HttpHeaders.CacheControl, "no-cache")
                header(HttpHeaders.XCorrelationId, callId())
            }
        }
    }

    val wellKnown: WellKnownResult by lazy {
        runBlocking {
            KotlinUtils.retry(10, 2.seconds) {
                log.info("Fetching oidc from ${config.wellKnownUrl}")
                httpClient.get(URL(config.wellKnownUrl)).body()
            }
        }
    }

    suspend fun refreshToken(refreshToken: String): TokenExchangeResult =
        withContext(Dispatchers.IO) {
            httpClient.post(wellKnown.tokenEndpoint) {
                setBody(
                    FormDataContent(
                        Parameters.build {
                            set("grant_type", "refresh_token")
                            set("client_id", config.clientId)
                            set("client_secret", config.clientSecret)
                            set("scope", "openid offline_access ${clientScope(config.clientId)}")
                            set("refresh_token", refreshToken)
                        }
                    )
                )
            }.body()
        }

    companion object {
        fun clientScope(clientId: String) = "api://$clientId/.default"
    }
}
