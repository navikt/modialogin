package no.nav.modialogin.features.oauthfeature

import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import no.nav.modialogin.auth.AzureAdConfig
import no.nav.modialogin.auth.OidcClient
import no.nav.modialogin.common.KtorUtils
import no.nav.modialogin.features.authfeature.BaseAuthProvider
import no.nav.modialogin.features.oauthfeature.OAuth.getOAuthTokens
import no.nav.modialogin.features.oauthfeature.OAuth.respondWithOAuthTokens
import no.nav.personoversikt.crypto.Crypter
import org.slf4j.LoggerFactory

class OAuthAuthProvider(
    override val name: String,
    private val appname: String,
    private val xForwardedPort: Int,
    private val config: AzureAdConfig
) : BaseAuthProvider() {
    private val log = LoggerFactory.getLogger("OAuthAuthProvider")
    private val client = OidcClient(config.toOidcClientConfig())
    private val crypter = config.cookieEncryptionKey?.let(::Crypter)

    override suspend fun getToken(call: ApplicationCall): String? {
        return getAllTokens(call)?.accessToken
    }

    override suspend fun getRefreshToken(call: ApplicationCall): String? {
        return getAllTokens(call)?.refreshToken
    }

    override fun verify(jwt: DecodedJWT) {
        check(jwt.audience.contains(config.clientId)) {
            "Audience mismatch, expected ${config.clientId} but got ${jwt.audience}"
        }
        check(jwt.issuer == config.openidConfigIssuer) {
            "Issuer mismatch, expected ${config.openidConfigIssuer} but got ${jwt.issuer}"
        }
    }

    override suspend fun refreshTokens(call: ApplicationCall, refreshToken: String): String {
        val newTokens = client.refreshToken(clientId = config.clientId, refreshToken = refreshToken)
        val cookieTokens = OAuth.CookieTokens(
            idToken = newTokens.idToken,
            accessToken = newTokens.accessToken,
            refreshToken = newTokens.refreshToken,
        )
        call.respondWithOAuthTokens(appname, crypter, cookieTokens)
        return newTokens.idToken
    }

    override suspend fun onUnauthorized(call: ApplicationCall) {
        val port = if (xForwardedPort == 8080) "" else ":$xForwardedPort"
        val originalUri =
            KtorUtils.encode("${call.request.origin.scheme}://${call.request.host()}$port${call.request.uri}")
        call.respondRedirect("/$appname/oauth2/login?redirect=$originalUri")
    }

    private val authCookies = AttributeKey<OAuth.CookieTokens>("OAuthAuthCookieTokens")
    private fun getAllTokens(call: ApplicationCall): OAuth.CookieTokens? {
        var cookies = call.attributes.getOrNull(authCookies)
        if (cookies == null) {
            cookies = call.getOAuthTokens(appname, crypter)
            if (cookies != null) {
                call.attributes.put(authCookies, cookies)
            }
        }
        return cookies
    }
}
