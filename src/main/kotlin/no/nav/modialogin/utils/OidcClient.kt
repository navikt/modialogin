package no.nav.modialogin.utils

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.features.auth.*
import io.ktor.client.features.auth.providers.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.features.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.nav.modialogin.EnvConfig

class OidcClient(private val envConfig: EnvConfig) {
    @Serializable
    class Config(
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
    private class RefreshIdTokenResponse(
        @SerialName("id_token") val idToken: String
    )

    private val client = HttpClient(CIO) {
        install(Auth) {
            basic {
                /**
                 * By default, ktor wait for the server to respond with 401 Unauthorized,
                 * and only then sends the authorization header.
                 * OIDC responds with 400 Bad request if the header is not present, hence why we need this configuration.
                 */
                sendWithoutRequest = true
                username = envConfig.idpClientId
                password = envConfig.idpClientSecret
            }
        }

        install(JsonFeature) {
            serializer = KotlinxSerializer(
                kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                    isLenient = true
                }
            )
        }

        defaultRequest {
            header(HttpHeaders.CacheControl, "no-cache")
        }
    }

    val config: Config = runBlocking {
        client.get(envConfig.idpDiscoveryUrl)
    }

    suspend fun openAmExchangeAuthCodeForToken(code: String, loginUrl: String,): TokenExchangeResult {
        return client.post(config.tokenEndpoint) {
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

    suspend fun refreshIdToken(refreshToken: String): String {
        val response: RefreshIdTokenResponse = client.post(config.tokenEndpoint) {
            body = FormDataContent(
                Parameters.build {
                    set("grant_type", "refresh_token")
                    set("scope", "openid")
                    set("realm", "/")
                    set("refresh_token", refreshToken)
                }
            )
        }
        return response.idToken
    }
}
