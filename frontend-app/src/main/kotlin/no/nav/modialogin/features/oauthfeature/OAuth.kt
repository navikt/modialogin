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
        @SerialName("id_token") val idToken: String,
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String,
    )

    fun ApplicationCall.respondWithOAuthTokens(appname: String, crypter: Crypter?, tokens: CookieTokens) {
        this.respondWithRawCookies(
            name = cookieName(appname, TokenType.ID_TOKEN),
            value = tokens.idToken,
            crypter = crypter,
        )
        this.respondWithRawCookies(
            name = cookieName(appname, TokenType.ACCESS_TOKEN),
            value = tokens.accessToken,
            crypter = crypter,
        )
        this.respondWithRawCookies(
            name = cookieName(appname, TokenType.REFRESH_TOKEN),
            value = tokens.refreshToken,
            crypter = crypter,
        )
    }

    fun ApplicationCall.getOAuthTokens(appname: String, crypter: Crypter?): CookieTokens? {
        val idToken = this.getCookie(cookieName(appname, TokenType.ID_TOKEN), crypter) ?: return null
        val accessToken = this.getCookie(cookieName(appname, TokenType.ACCESS_TOKEN), crypter) ?: return null
        val refreshToken = this.getCookie(cookieName(appname, TokenType.REFRESH_TOKEN), crypter) ?: return null

        return CookieTokens(
            idToken = idToken,
            accessToken = accessToken,
            refreshToken = refreshToken,
        )
    }

    private fun ApplicationCall.respondWithRawCookies(name: String, value: String, crypter: Crypter?) {
        this.respondWithCookie(
            name = name,
            value = value,
            crypter = crypter
        )

        // TODO only include non-encrypted cookies in dev
        this.respondWithCookie(
            name = "${name}_RAW",
            value = value
        )
    }

    private enum class TokenType {
        ID_TOKEN, ACCESS_TOKEN, REFRESH_TOKEN
    }
}
