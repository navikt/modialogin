package no.nav.modialogin.features.authfeature

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.date.*
import no.nav.modialogin.common.KtorServer
import no.nav.modialogin.common.KtorUtils
import no.nav.modialogin.common.KtorUtils.respondWithCookie
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class DelegatedAuthProvider(
    override val name: String,
    private val xForwardedPort: Int,
    private val startLoginUrl: String,
    refreshUrl: String,
    private val authTokenResolver: String,
    private val refreshTokenResolver: String?,
    private val acceptedAudience: String,
    private val acceptedIssuer: String,
) : AuthProvider {
    private val refreshClient = DelegatedRefreshClient(refreshUrl)

    override suspend fun authorize(call: ApplicationCall): AuthFilterPrincipal? {
        var token: String = call.request.cookies[authTokenResolver, CookieEncoding.RAW] ?: return null
        val jwt: DecodedJWT = JWT.decode(token)

        if (!jwt.audience.contains(acceptedAudience)) {
            KtorServer.log.warn("Audience mismatch, expected $acceptedAudience but got ${jwt.audience}")
            return null
        }
        if (jwt.issuer != acceptedIssuer) {
            KtorServer.log.warn("Issuer mismatch, expected $acceptedIssuer but got ${jwt.issuer}")
            return null
        }

        val refreshToken = refreshTokenResolver?.let { call.request.cookies[it, CookieEncoding.RAW] }
        if (refreshTokenResolver != null && refreshToken != null && jwt.expiresWithin(5.minutes)) {
            val newToken = refreshClient.refreshToken(refreshToken)
            call.respondWithCookie(
                name = authTokenResolver,
                value = newToken
            )
        }

        return AuthFilterPrincipal(name, token)
    }

    override suspend fun onUnauthorized(call: ApplicationCall) {
        val port = if (xForwardedPort == 8080) "" else ":$xForwardedPort"
        val originalUri = KtorUtils.encode("${call.request.origin.scheme}://${call.request.host()}$port${call.request.uri}")
        call.respondRedirect("$startLoginUrl?url=$originalUri")
    }

    private fun DecodedJWT.expiresWithin(time: Duration): Boolean {
        val expiry = this.expiresAt.time - time.inWholeMilliseconds
        return getTimeMillis() > expiry
    }
}
