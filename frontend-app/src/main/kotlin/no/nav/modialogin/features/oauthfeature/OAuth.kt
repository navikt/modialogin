package no.nav.modialogin.features.oauthfeature

import io.ktor.server.application.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.nav.modialogin.common.KtorUtils.getCookie
import no.nav.modialogin.common.KtorUtils.respondWithCookie
import no.nav.personoversikt.crypto.Crypter

object OAuth {
    private fun cookieName(appname: String, tokenType: TokenType): String = "${appname}_${tokenType.name}"

    @Serializable
    class CookieTokens(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String,
    )

    fun ApplicationCall.respondWithOAuthTokens(appname: String, crypter: Crypter?, tokens: CookieTokens) {
        this.respondWithCookie(
            name = cookieName(appname, TokenType.ACCESS_TOKEN),
            value = tokens.accessToken,
            crypter = crypter,
        )
        this.respondWithCookie(
            name = cookieName(appname, TokenType.REFRESH_TOKEN),
            value = tokens.refreshToken,
            crypter = crypter,
        )
    }

    fun ApplicationCall.getOAuthTokens(appname: String, crypter: Crypter?): CookieTokens? {
        val accessToken = this.getCookie(cookieName(appname, TokenType.ACCESS_TOKEN), crypter) ?: return null
        val refreshToken = this.getCookie(cookieName(appname, TokenType.REFRESH_TOKEN), crypter) ?: return null

        return CookieTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
        )
    }

    private enum class TokenType {
        ACCESS_TOKEN, REFRESH_TOKEN
    }
}
