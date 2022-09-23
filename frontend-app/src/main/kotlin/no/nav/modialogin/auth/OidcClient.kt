package no.nav.modialogin.auth

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
import no.nav.modialogin.common.*
import no.nav.modialogin.common.KotlinUtils.callId
import java.net.URL
import kotlin.time.Duration.Companion.seconds

class OidcClient(val config: Config) {
    sealed class Url(val url: String) {
        class External(url: String) : Url(url)
        class Internal(url: String) : Url(url)
    }
    class Config(
        val clientId: String,
        val clientSecret: String,
        val wellKnownUrl: Url
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

    private val httpClient: HttpClient by lazy {
        HttpClient(Apache) {
            if (config.wellKnownUrl is Url.External) {
                useProxy()
            }
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
                KtorServer.log.info("Fetching oidc from ${config.wellKnownUrl}")
                httpClient.get(URL(config.wellKnownUrl.url)).body()
            }
        }
    }

    suspend fun exchangeAuthCodeForToken(clientId: String, code: String, callbackUrl: String): TokenExchangeResult =
        withContext(Dispatchers.IO) {
            httpClient.post(wellKnown.tokenEndpoint) {
                setBody(
                    FormDataContent(
                        Parameters.build {
                            set("grant_type", "authorization_code")
                            set("client_id", config.clientId)
                            set("client_secret", config.clientSecret)
                            set("redirect_uri", callbackUrl)
                            set("code", code)
                            set("scope", "openid offline_access ${clientScope(clientId)}")
                        }
                    )
                )
            }.body()
        }

    suspend fun refreshToken(clientId: String, refreshToken: String): TokenExchangeResult =
        withContext(Dispatchers.IO) {
            httpClient.post(wellKnown.tokenEndpoint) {
                setBody(
                    FormDataContent(
                        Parameters.build {
                            set("grant_type", "refresh_token")
                            set("client_id", config.clientId)
                            set("client_secret", config.clientSecret)
                            set("scope", "openid offline_access ${clientScope(clientId)}")
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
