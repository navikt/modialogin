package no.nav.modialogin.features.authfeature

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import no.nav.modialogin.common.KotlinUtils.callId
import no.nav.modialogin.common.KtorServer.tjenestekallLogger
import no.nav.modialogin.common.json
import java.net.URL

class DelegatedRefreshClient(url: String) {
    val url = URL(url)

    @Serializable
    private class RefreshIdTokenResponse(val idToken: String)
    @Serializable
    private class RefreshIdTokenRequest(val refreshToken: String)

    private val client = HttpClient(Apache) {
        json()
        defaultRequest {
            headers {
                append(HttpHeaders.XCorrelationId, callId())
            }
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
