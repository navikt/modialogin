package no.nav.modialogin.features

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable
import no.nav.modialogin.Config
import no.nav.modialogin.Utils
import no.nav.modialogin.Utils.removeCookie
import no.nav.modialogin.Utils.respondWithCookie
import java.math.BigInteger
import kotlin.random.Random

object LoginFeature {
    fun Application.installLoginFeature(config: Config) {
        routing {
            route(config.env.appname) {
                route("api") {
                    get("start") { startLoginFlowAgainstIDP(config) }
                    get("login") { handleCallbackFromIDP(config) }
                    post("refresh") { refreshToken(config) }
                }
            }
        }
    }

    fun generateStateNounce(): String {
        val bytes = Random.nextBytes(20)
        return "state_${BigInteger(1, bytes).toString(16)}"
    }

    fun loginUrl(request: ApplicationRequest, config: Config): String {
        // Only localhost val port = request.port().let { if (it == 80) "" else ":$it" }
        val port = ""
        return "https://${request.host()}$port/${config.env.appname}/api/login"
    }

    fun callbackUrl(authorizationEndpoint: String, clientId: String, stateNounce: String, callbackUrl: String): String {
        val requestParameters = Parameters.build {
            set("client_id", clientId)
            set("state", stateNounce)
            set("redirect_uri", callbackUrl)
        }

        val parameters = kerberosTriggerParameters + openidCodeFlowParameters + requestParameters
        return "$authorizationEndpoint?${parameters.formUrlEncode()}"
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.startLoginFlowAgainstIDP(config: Config) {
        val returnUrl: String = requireNotNull(call.request.queryParameters["url"]) {
            "URL parameter 'url' is missing"
        }
        val stateNounce = generateStateNounce()
        call.respondWithCookie(
            name = stateNounce,
            value = Utils.encode(returnUrl)
        )

        val redirectUrl = callbackUrl(
            authorizationEndpoint = config.oidc.config.authorizationEndpoint,
            clientId = config.env.idpClientId,
            stateNounce = stateNounce,
            callbackUrl = loginUrl(call.request, config)
        )
        call.respondRedirect(permanent = false, url = redirectUrl)
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.handleCallbackFromIDP(config: Config) {
        val code: String = requireNotNull(call.request.queryParameters["code"]) {
            "URL parameter 'code' is missing"
        }
        val state: String = requireNotNull(call.request.queryParameters["state"]) {
            "URL parameter 'state' is missing"
        }
        val cookie = requireNotNull(call.request.cookies[state]) {
            "State-cookie is missing"
        }

        val token = config.oidc.openAmExchangeAuthCodeForToken(
            code = code,
            loginUrl = loginUrl(call.request, config)
        )
        call.respondWithCookie(
            name = "ID_token",
            value = token.idToken
        )
        if (token.refreshToken != null) {
            call.respondWithCookie(
                name = "refresh_token",
                value = token.refreshToken
            )
        }
        call.removeCookie(state)

        val originalUrl = Utils.decode(cookie)
        call.respondRedirect(
            permanent = false,
            url = originalUrl
        )
    }

    @Serializable
    private class RefreshIdTokenRequest(val refreshToken: String)
    @Serializable
    private class RefreshIdTokenResponse(val idToken: String)
    private suspend fun PipelineContext<Unit, ApplicationCall>.refreshToken(config: Config) {
        val body = call.receive<RefreshIdTokenRequest>()
        val newIdToken = config.oidc.refreshIdToken(body.refreshToken)
        call.respond(RefreshIdTokenResponse(newIdToken))
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
