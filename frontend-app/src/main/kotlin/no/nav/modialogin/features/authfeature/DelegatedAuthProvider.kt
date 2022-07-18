package no.nav.modialogin.features.authfeature

import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import no.nav.modialogin.common.KtorUtils
import no.nav.modialogin.common.KtorUtils.getCookie
import no.nav.modialogin.common.KtorUtils.respondWithCookie

class DelegatedAuthProvider(
    override val name: String,
    private val xForwardedPort: Int,
    private val startLoginUrl: String,
    refreshUrl: String,
    private val authTokenResolver: String,
    private val refreshTokenResolver: String?,
    private val acceptedAudience: String,
    private val acceptedIssuer: String,
) : BaseAuthProvider() {
    private val refreshClient = DelegatedRefreshClient(refreshUrl)

    override suspend fun getToken(call: ApplicationCall): String? {
        if (authTokenResolver == "header") {
            return call.request.authorization()
        }
        return call.getCookie(authTokenResolver)
    }

    override fun verify(jwt: DecodedJWT) {
        check(jwt.audience.contains(acceptedAudience)) {
            "Audience mismatch, expected $acceptedAudience but got ${jwt.audience}"
        }
        check(jwt.issuer == acceptedIssuer) {
            "Issuer mismatch, expected $acceptedIssuer but got ${jwt.issuer}"
        }
    }

    override suspend fun getRefreshToken(call: ApplicationCall): String? {
        return refreshTokenResolver?.let { call.getCookie(refreshTokenResolver) }
    }

    override suspend fun refreshTokens(call: ApplicationCall, refreshToken: String): String {
        val newToken = refreshClient.refreshToken(refreshToken)
        call.respondWithCookie(
            name = authTokenResolver,
            value = newToken,
            encoding = CookieEncoding.RAW,
        )
        return newToken
    }

    override suspend fun onUnauthorized(call: ApplicationCall) {
        val port = if (xForwardedPort == 8080) "" else ":$xForwardedPort"
        val originalUri = KtorUtils.encode("${call.request.origin.scheme}://${call.request.host()}$port${call.request.uri}")
        call.respondRedirect("$startLoginUrl?redirect=$originalUri")
    }
}
