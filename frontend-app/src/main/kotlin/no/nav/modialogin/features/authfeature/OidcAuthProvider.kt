package no.nav.modialogin.features.authfeature

import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import no.nav.modialogin.auth.OidcClient
import no.nav.modialogin.auth.OidcConfig
import no.nav.modialogin.common.KtorUtils
import no.nav.modialogin.common.KtorUtils.getCookie
import no.nav.modialogin.common.KtorUtils.respondWithCookie
import kotlin.time.Duration.Companion.hours

class OidcAuthProvider(
    override val name: String,
    private val appname: String,
    private val xForwardedPort: Int,
    private val authTokenResolver: String,
    private val refreshTokenResolver: String?,
    private val config: OidcConfig,
) : BaseAuthProvider() {
    private val client = OidcClient(config)

    override suspend fun getToken(call: ApplicationCall): String? {
        return call.getCookie(authTokenResolver)
    }

    override fun verify(jwt: DecodedJWT) {
        check(jwt.audience.contains(config.clientId)) {
            "Audience mismatch, expected ${config.clientId} but got ${jwt.audience}"
        }
        check(jwt.issuer == config.openidConfigIssuer) {
            "Issuer mismatch, expected ${config.openidConfigIssuer} but got ${jwt.issuer}"
        }
    }

    override suspend fun getRefreshToken(call: ApplicationCall): String? {
        return refreshTokenResolver?.let { call.getCookie(refreshTokenResolver) }
    }

    override suspend fun refreshTokens(call: ApplicationCall, refreshToken: String): String {
        val newTokens = client.refreshToken(refreshToken)
        call.respondWithCookie(
            name = authTokenResolver,
            value = requireNotNull(newTokens.accessToken)
        )
        call.respondWithCookie(
            name = checkNotNull(refreshTokenResolver),
            value = newTokens.refreshToken,
            maxAgeInSeconds = 20.hours.inWholeSeconds.toInt()
        )
        return newTokens.accessToken
    }

    override suspend fun onUnauthorized(call: ApplicationCall) {
        val port = if (xForwardedPort == 8080) "" else ":$xForwardedPort"
        val originalUri = KtorUtils.encode("${call.request.origin.scheme}://${call.request.host()}$port${call.request.uri}")
        call.respondRedirect("/$appname/oauth2/login?redirect=$originalUri")
    }
}
