package no.nav.modialogin.features

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable
import no.nav.modialogin.oidc.OidcClient
import no.nav.modialogin.utils.KtorUtils
import no.nav.modialogin.utils.KtorUtils.removeCookie
import no.nav.modialogin.utils.KtorUtils.respondWithCookie
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
    )

    private val oidcClient = OidcClient.TokenExchangeClient(
        OidcClient.TokenExchangeConfig(
            discoveryUrl = config.idpDiscoveryUrl,
            clientId = config.idpClientId,
            clientSecret = config.idpClientSecret
        )
    )

    fun install(app: Application) {
        with(app) {
            routing {
                route(config.appname) {
                    route("api") {
                        get("start") { startLoginFlowAgainstIDP() }
                        get("login") { handleCallbackFromIDP() }
                        post("refresh") { refreshToken() }
                    }
                }
            }
        }
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.startLoginFlowAgainstIDP() {
        val returnUrl: String = requireNotNull(call.request.queryParameters["url"]) {
            "URL parameter 'url' is missing"
        }
        val stateNounce = generateStateNounce()
        call.respondWithCookie(
            name = stateNounce,
            value = KtorUtils.encode(returnUrl)
        )

        val redirectUrl = callbackUrl(
            authorizationEndpoint = oidcClient.jwksConfig.authorizationEndpoint,
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
        val cookie = requireNotNull(call.request.cookies[state]) {
            "State-cookie is missing"
        }

        val token = oidcClient.openAmExchangeAuthCodeForToken(
            code = code,
            loginUrl = loginUrl(call.request, config.exposedPort, config.appname)
        )
        call.respondWithCookie(
            name = config.authTokenResolver,
            value = token.idToken
        )
        if (token.refreshToken != null) {
            call.respondWithCookie(
                name = config.refreshTokenResolver,
                value = token.refreshToken
            )
        }
        call.removeCookie(state)

        val originalUrl = KtorUtils.decode(cookie)
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
        val newIdToken = oidcClient.refreshIdToken(body.refreshToken)
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
