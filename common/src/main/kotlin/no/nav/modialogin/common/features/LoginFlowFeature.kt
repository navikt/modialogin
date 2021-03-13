package no.nav.modialogin.common.features

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable
import no.nav.modialogin.common.KtorUtils
import no.nav.modialogin.common.KtorUtils.removeCookie
import no.nav.modialogin.common.KtorUtils.respondWithCookie
import no.nav.modialogin.common.LoginStateHelpers.callbackUrl
import no.nav.modialogin.common.LoginStateHelpers.generateStateNounce
import no.nav.modialogin.common.LoginStateHelpers.loginUrl
import no.nav.modialogin.common.Oidc

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
        val xForwardingPort: Int,
    )

    private val oidcClient = Oidc.TokenExchangeClient(
        Oidc.TokenExchangeConfig(
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
            callbackUrl = loginUrl(call.request, config.xForwardingPort, config.appname)
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
            loginUrl = loginUrl(call.request, config.xForwardingPort, config.appname)
        )
        call.respondWithCookie(
            name = ModiaLoginConstants.idToken,
            value = token.idToken
        )
        if (token.refreshToken != null) {
            call.respondWithCookie(
                name = ModiaLoginConstants.refreshToken,
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
}
