package no.nav.modialogin.features.authfeature

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import no.nav.modialogin.common.KtorServer.tjenestekallLogger
import no.nav.modialogin.common.KtorUtils
import no.nav.modialogin.common.KtorUtils.getCookie
import no.nav.modialogin.common.KtorUtils.removeCookie
import no.nav.modialogin.common.KtorUtils.respondWithCookie
import kotlin.time.Duration.Companion.seconds

class DelegatedAuthProvider(
    override val name: String,
    private val xForwardedPort: Int,
    private val startLoginUrl: String,
    refreshUrl: String,
    wellKnownUrl: String,
    private val authTokenResolver: String,
    private val refreshTokenResolver: String?,
    private val acceptedAudience: String,
    private val acceptedIssuer: String,
) : BaseAuthProvider(wellKnownUrl) {
    private val refreshClient = DelegatedRefreshClient(refreshUrl)

    override suspend fun getToken(call: ApplicationCall): String? {
        if (authTokenResolver == "header") {
            return call.request.authorization()
        }
        return call.getCookie(authTokenResolver, CookieEncoding.RAW)
    }

    override suspend fun getRefreshToken(call: ApplicationCall): String? {
        return refreshTokenResolver?.let { call.getCookie(refreshTokenResolver, CookieEncoding.RAW) }
    }

    override fun verify(token: String): DecodedJWT{
        val jwt = JWT.decode(token)
        val jwk = jwkProvider.get(jwt.keyId)
        return JWT
            .require(jwk.makeAlgorithm())
            .withAudience(acceptedAudience)
            .withIssuer(acceptedIssuer)
            .acceptLeeway(60.seconds.inWholeSeconds)
            .build()
            .verify(jwt)
    }

    override suspend fun refreshTokens(call: ApplicationCall, refreshToken: String): String {
        val token = refreshClient.refreshToken(refreshToken)

        if (token == null && refreshTokenResolver != null) {
            tjenestekallLogger.warn("Refreshing of token failed, removing refresh token '$refreshToken' from cookie '$refreshTokenResolver'")
            call.removeCookie(name = refreshTokenResolver)
        }

        val newToken = checkNotNull(token) {
            "New Token cannot be null"
        }

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
