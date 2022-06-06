package no.nav.modialogin.features.authfeature

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.date.*
import no.nav.modialogin.auth.OidcClient
import no.nav.modialogin.auth.OidcConfig
import no.nav.modialogin.common.KtorServer.log
import no.nav.modialogin.common.KtorUtils
import no.nav.modialogin.common.KtorUtils.respondWithCookie
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class OidcAuthProvider(
    override val name: String,
    private val appname: String,
    private val xForwardedPort: Int,
    private val authTokenResolver: String,
    private val refreshTokenResolver: String?,
    private val config: OidcConfig,
) : AuthProvider {
    private val client = OidcClient(config)

    override suspend fun authorize(call: ApplicationCall): AuthFilterPrincipal? {
        var token: String = call.request.cookies[authTokenResolver, CookieEncoding.RAW] ?: return null
        val jwt: DecodedJWT = JWT.decode(token)

        if (!jwt.audience.contains(config.clientId)) {
            log.warn("Audience mismatch, expected ${config.clientId} but got ${jwt.audience}")
            return null
        }
        if (jwt.issuer != config.openidConfigIssuer) {
            log.warn("Issuer mismatch, expected ${config.openidConfigIssuer} but got ${jwt.issuer}")
            return null
        }

        val refreshToken = refreshTokenResolver?.let { call.request.cookies[it, CookieEncoding.RAW] }
        if (refreshTokenResolver != null && refreshToken != null && jwt.expiresWithin(5.minutes)) {
            val newTokens = client.refreshToken(refreshToken)
            call.respondWithCookie(
                name = authTokenResolver,
                value = requireNotNull(newTokens.accessToken)
            )
            if (newTokens.refreshToken != null) {
                call.respondWithCookie(
                    name = refreshTokenResolver,
                    value = newTokens.refreshToken,
                    maxAgeInSeconds = 20.hours.inWholeSeconds.toInt()
                )
            }
        }

        return AuthFilterPrincipal(name, token)
    }

    override suspend fun onUnauthorized(call: ApplicationCall) {
        val port = if (xForwardedPort == 8080) "" else ":$xForwardedPort"
        val originalUri =
            KtorUtils.encode("${call.request.origin.scheme}://${call.request.host()}$port${call.request.uri}")
        call.respondRedirect("/$appname/oauth2/login?redirect=$originalUri")
    }

    private fun DecodedJWT.expiresWithin(time: Duration): Boolean {
        val expiry = this.expiresAt.time - time.inWholeMilliseconds
        return getTimeMillis() > expiry
    }
}
