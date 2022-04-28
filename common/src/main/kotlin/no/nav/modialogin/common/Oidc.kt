package no.nav.modialogin.common

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.features.auth.*
import io.ktor.client.features.auth.providers.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.nav.modialogin.common.KtorServer.log

sealed class Oidc {
    @Serializable
    class JwksConfig(
        @SerialName("jwks_uri") val jwksUrl: String,
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

    @Serializable
    class RefreshIdTokenResponse(
        @SerialName("id_token") val idToken: String
    )

    open class JwksClientConfig(val discoveryUrl: String)
    class TokenExchangeConfig(
        discoveryUrl: String,
        val clientId: String,
        val clientSecret: String,
    ) : JwksClientConfig(discoveryUrl)

    open class JwksClient(config: JwksClientConfig) {
        private val client = HttpClient(CIO) {
            json()
            defaultRequest {
                header(HttpHeaders.CacheControl, "no-cache")
            }
        }

        val jwksConfig: JwksConfig = runBlocking {
            log.info("Fetching oidc from ${config.discoveryUrl}")
            client.get(config.discoveryUrl)
        }
    }

    class TokenExchangeClient(private val config: TokenExchangeConfig) : JwksClient(config) {
        private val authenticatedClient: HttpClient by lazy {
            HttpClient(CIO) {
                basicAuth(config.clientId, requireNotNull(config.clientSecret))
                json()
                defaultRequest {
                    header(HttpHeaders.CacheControl, "no-cache")
                }
            }
        }

        suspend fun openAmExchangeAuthCodeForToken(code: String, loginUrl: String): TokenExchangeResult =
            withContext(Dispatchers.IO) {
                authenticatedClient.post(jwksConfig.tokenEndpoint) {
                    body = FormDataContent(
                        Parameters.build {
                            set("grant_type", "authorization_code")
                            set("realm", "/")
                            set("redirect_uri", loginUrl)
                            set("code", code)
                        }
                    )
                }
            }

        suspend fun refreshIdToken(refreshToken: String): String =
            withContext(Dispatchers.IO) {
                val response: RefreshIdTokenResponse = authenticatedClient.post(jwksConfig.tokenEndpoint) {
                    body = FormDataContent(
                        Parameters.build {
                            set("grant_type", "refresh_token")
                            set("scope", "openid")
                            set("realm", "/")
                            set("refresh_token", refreshToken)
                        }
                    )
                }
                response.idToken
            }
    }
}

private fun <T : HttpClientEngineConfig> HttpClientConfig<T>.basicAuth(clientId: String, clientSecret: String) {
    install(Auth) {
        basic {
            /**
             * By default, ktor wait for the server to respond with 401 Unauthorized,
             * and only then sends the authorization header.
             * OIDC responds with 400 Bad request if the header is not present, hence why we need this configuration.
             */
            sendWithoutRequest = true
            username = clientId
            password = clientSecret
        }
    }
}

private fun <T : HttpClientEngineConfig> HttpClientConfig<T>.json() {
    install(JsonFeature) {
        serializer = KotlinxSerializer(
            kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            }
        )
    }
}
