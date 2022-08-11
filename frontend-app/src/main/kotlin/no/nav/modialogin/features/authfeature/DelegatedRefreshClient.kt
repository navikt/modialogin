package no.nav.modialogin.features.authfeature

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.nav.modialogin.common.KtorServer.tjenestekallLogger
import java.net.URL

class DelegatedRefreshClient(url: String) {
    val url = URL(url)

    @Serializable
    private class RefreshIdTokenResponse(val idToken: String)
    @Serializable
    private class RefreshIdTokenRequest(val refreshToken: String)

    private val client = HttpClient(Apache) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }
            )
        }
    }

    suspend fun refreshToken(refreshToken: String): String? {
        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(
                RefreshIdTokenRequest(refreshToken)
            )
        }
        if (!response.status.isSuccess()) {
            tjenestekallLogger.error("Could not refresh token for $refreshToken")
            return null
        }
        return response.body<RefreshIdTokenResponse>().idToken
    }
}
