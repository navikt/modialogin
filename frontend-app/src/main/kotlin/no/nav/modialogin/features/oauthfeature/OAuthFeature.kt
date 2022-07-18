package no.nav.modialogin.features.oauthfeature

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.modialogin.auth.OidcClient
import no.nav.modialogin.auth.OidcClient.Companion.clientScope
import no.nav.modialogin.common.KtorServer
import no.nav.modialogin.common.KtorUtils
import no.nav.modialogin.common.KtorUtils.getCookie
import no.nav.modialogin.common.KtorUtils.removeCookie
import no.nav.modialogin.common.KtorUtils.respondWithCookie
import no.nav.modialogin.features.oauthfeature.OAuth.CookieTokens
import no.nav.modialogin.features.oauthfeature.OAuth.respondWithOAuthTokens
import no.nav.personoversikt.crypto.Crypter
import java.math.BigInteger
import kotlin.random.Random

class OAuthFeature(private val config: Config) {
    companion object {
        fun Application.installOAuthRoutes(config: Config) {
            OAuthFeature(config).install(this)
        }
    }

    class Config(
        val appname: String,
        val oidc: OidcClient,
        val cookieEncryptionKey: String?,
        val exposedPort: Int,
    )

    private val crypter = config.cookieEncryptionKey?.let(::Crypter)

    fun install(app: Application) = with(app) {
        routing {
            route(config.appname) {
                route("oauth2") {
                    get("login") {
                        val returnUrl: String =
                            call.request.queryParameters["redirect"]
                                ?: call.request.header("Referer")
                                ?: "/"

                        val stateNounce = generateStateNounce()
                        call.respondWithCookie(
                            name = stateNounce,
                            value = KtorUtils.encode(returnUrl)
                        )
                        KtorServer.log.info("Starting loginflow: $stateNounce -> $returnUrl")
                        val redirectUrl = callbackUrl(
                            authorizationEndpoint = config.oidc.wellKnown.authorizationEndpoint,
                            clientId = config.oidc.config.clientId,
                            stateNounce = stateNounce,
                            callbackUrl = loginUrl(call.request, config.exposedPort, config.appname)
                        )
                        call.respondRedirect(permanent = false, url = redirectUrl)
                    }
                    get("callback") {
                        val code: String = requireNotNull(call.request.queryParameters["code"]) {
                            "URL parameter 'code' is missing"
                        }
                        val state: String = requireNotNull(call.request.queryParameters["state"]) {
                            "URL parameter 'state' is missing"
                        }

                        val cookie = requireNotNull(call.getCookie(state)) {
                            "State-cookie is missing"
                        }
                        val originalUrl = KtorUtils.decode(cookie)

                        KtorServer.log.info("Callback from IDP: $state -> $originalUrl")
                        val tokens = config.oidc.exchangeAuthCodeForToken(
                            code = code,
                            clientId = config.oidc.config.clientId,
                            callbackUrl = loginUrl(call.request, config.exposedPort, config.appname)
                        )

                        val cookieTokens = CookieTokens(
                            accessToken = tokens.accessToken,
                            refreshToken = tokens.refreshToken,
                        )
                        call.respondWithOAuthTokens(config.appname, crypter, cookieTokens)
                        call.removeCookie(state)

                        call.respondRedirect(
                            permanent = false,
                            url = originalUrl
                        )
                    }
                }
            }
        }
    }

    private fun loginUrl(request: ApplicationRequest, exposedPort: Int, appname: String): String {
        // exposedPort == 8080, hints at the app running on nais. Hence we remove port for the url, and enforce https.
        // within docker-compose, exposedPort will typically be something other than 8080 and thus we append it to the url.
        val port = if (exposedPort == 8080) "" else ":$exposedPort"
        val scheme = if (exposedPort == 8080) "https" else "http"
        return "$scheme://${request.host()}$port/$appname/oauth2/callback"
    }

    private fun callbackUrl(
        authorizationEndpoint: String,
        clientId: String,
        stateNounce: String,
        callbackUrl: String
    ): String {
        val requestParameters = Parameters.build {
            set("client_id", clientId)
            set("response_type", "code")
            set("response_mode", "query")
            set("scope", "openid offline_access ${clientScope(clientId)}")
            set("state", stateNounce)
            set("redirect_uri", callbackUrl)
        }

        return "$authorizationEndpoint?${requestParameters.formUrlEncode()}"
    }

    private fun generateStateNounce(): String {
        val bytes = Random.nextBytes(20)
        return "state_${BigInteger(1, bytes).toString(16)}"
    }
}
