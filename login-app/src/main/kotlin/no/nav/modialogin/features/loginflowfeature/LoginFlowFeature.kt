package no.nav.modialogin.features.loginflowfeature

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable
import no.nav.modialogin.common.KtorServer
import no.nav.modialogin.common.KtorServer.log
import no.nav.modialogin.common.KtorUtils
import no.nav.modialogin.common.KtorUtils.getCookie
import no.nav.modialogin.common.KtorUtils.removeCookie
import no.nav.modialogin.common.KtorUtils.respondWithCookie
import java.math.BigInteger
import kotlin.random.Random

class LoginFlowFeature(private val config: Config) {
    companion object {
        fun Application.installLoginFlowFeature(config: Config) {
            LoginFlowFeature(config).install(this)
        }
    }

    class Config(
        val appname: String,
        val idpDiscoveryUrl: String,
        val idpClientId: String,
        val idpClientSecret: String,
        val authTokenResolver: String,
        val refreshTokenResolver: String,
        val exposedPort: Int,
        val urls: UrlConfig = UrlConfig()
    )
    class UrlConfig(
        val start: String = "api/start",
        val callback: String = "api/login",
        val refresh: String = "api/refresh",
    )

    private val openAmClient = OpenAmClient.TokenExchangeClient(
        OpenAmClient.TokenExchangeConfig(
            discoveryUrl = config.idpDiscoveryUrl,
            clientId = config.idpClientId,
            clientSecret = config.idpClientSecret
        )
    )

    fun install(app: Application) {
        with(app) {
            routing {
                route(config.appname) {
                    get(config.urls.start) { startLoginFlowAgainstIDP() }
                    get(config.urls.callback) { handleCallbackFromIDP() }
                    post(config.urls.refresh) { refreshToken() }
                }
            }
        }
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.startLoginFlowAgainstIDP() {
        val returnUrl: String = requireNotNull(call.request.queryParameters["redirect"] ?: call.request.queryParameters["url"]) {
            "URL parameter 'redirect'/'url' is missing"
        }
        val stateNounce = generateStateNounce()
        call.respondWithCookie(
            name = stateNounce,
            value = KtorUtils.encode(returnUrl)
        )
        log.info("Starting loginflow: $stateNounce -> $returnUrl")
        val redirectUrl = callbackUrl(
            authorizationEndpoint = openAmClient.wellknown.authorizationEndpoint,
            clientId = config.idpClientId,
            stateNounce = stateNounce,
            callbackUrl = loginUrl(call.request, config.exposedPort, config.appname)
        )
        call.respondRedirect(permanent = false, url = redirectUrl)
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.handleCallbackFromIDP() {
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

        log.info("Callback from IDP: $state -> $originalUrl")
        val token = openAmClient.openAmExchangeAuthCodeForToken(
            code = code,
            loginUrl = loginUrl(call.request, config.exposedPort, config.appname)
        )
        call.respondWithCookie(
            name = config.authTokenResolver,
            value = token.idToken,
            encoding = CookieEncoding.RAW,
        )
        if (token.refreshToken != null) {
            call.respondWithCookie(
                name = config.refreshTokenResolver,
                value = token.refreshToken,
                maxAgeInSeconds = 20 * 3600,
                encoding = CookieEncoding.RAW,
            )
        }
        call.removeCookie(state)

        call.respondRedirect(
            permanent = false,
            url = originalUrl
        )
    }

    @Serializable
    private class RefreshIdTokenRequest(val refreshToken: String)

    @Serializable
    private class RefreshIdTokenResponse(val idToken: String)

    private suspend fun PipelineContext<Unit, ApplicationCall>.refreshToken() {
        val body = call.receive<RefreshIdTokenRequest>()
        val newIdToken = openAmClient
            .runCatching  { refreshIdToken(body.refreshToken).idToken }
            .onFailure { KtorServer.tjenestekallLogger.error("Failed to refresh token: ${body.refreshToken}") }
            .getOrThrow()
        call.respond(RefreshIdTokenResponse(newIdToken))
    }

    private fun generateStateNounce(): String {
        val bytes = Random.nextBytes(20)
        return "state_${BigInteger(1, bytes).toString(16)}"
    }

    private fun callbackUrl(authorizationEndpoint: String, clientId: String, stateNounce: String, callbackUrl: String): String {
        val requestParameters = Parameters.build {
            set("client_id", clientId)
            set("state", stateNounce)
            set("redirect_uri", callbackUrl)
        }

        val parameters = kerberosTriggerParameters + openidCodeFlowParameters + requestParameters
        return "$authorizationEndpoint?${parameters.formUrlEncode()}"
    }

    private fun loginUrl(request: ApplicationRequest, exposedPort: Int, appname: String): String {
        // exposedPort == 8080, hints at the app running on nais. Hence we remove port for the url, and enforce https.
        // within docker-compose, exposedPort will typically be something other than 8080 and thus we append it to the url.
        val port = if (exposedPort == 8080) "" else ":$exposedPort"
        val scheme = if (exposedPort == 8080) "https" else "http"
        return "$scheme://${request.host()}$port/$appname/api/login"
    }

    private val kerberosTriggerParameters = Parameters.build {
        set("session", "winssochain")
        set("authIndexType", "service")
        set("authIndexValue", "winssochain")
    }
    private val openidCodeFlowParameters = Parameters.build {
        set("response_type", "code")
        set("scope", "openid")
    }
}
