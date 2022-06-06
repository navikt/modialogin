package no.nav.modialogin.common

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

fun <T : HttpClientEngineConfig> HttpClientConfig<T>.basicAuth(clientId: String, clientSecret: String) {
    val basicAuthCredentials = BasicAuthCredentials(clientId, clientSecret)
    install(Auth) {
        basic {
            /**
             * By default, ktor wait for the server to respond with 401 Unauthorized,
             * and only then sends the authorization header.
             * OIDC responds with 400 Bad request if the header is not present, hence why we need this configuration.
             */
            sendWithoutRequest { true }
            credentials { basicAuthCredentials }
        }
    }
}

fun <T : HttpClientEngineConfig> HttpClientConfig<T>.json() {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            }
        )
    }
}

fun <T : HttpClientEngineConfig> HttpClientConfig<T>.logging() {
    install(Logging) {
        level = LogLevel.ALL
        logger = object : Logger {
            override fun log(message: String) {
                KtorServer.tjenestekallLogger.info(message)
            }
        }
    }
}
