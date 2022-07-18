package no.nav.modialogin.features.oauthfeature

import io.ktor.server.application.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.modialogin.common.KtorServer.log
import no.nav.modialogin.common.KtorUtils.getCookie
import no.nav.modialogin.common.KtorUtils.respondWithCookie
import no.nav.personoversikt.crypto.Crypter

object OAuth {
    fun cookieName(appname: String): String = "${appname}_tokens"

    @Serializable
    class CookieTokens(
        @SerialName("id_token") val idToken: String,
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String,
    )

    fun ApplicationCall.respondWithOAuthTokens(appname: String, crypter: Crypter?, tokens: CookieTokens) {
        this.respondWithCookie(
            name = cookieName(appname),
            value = Json.encodeToString(tokens),
            crypter = crypter,
        )
        // TODO only include non-encrypted cookies in dev
        this.respondWithCookie(
            name = "${cookieName(appname)}_raw",
            value = Json.encodeToString(tokens),
        )
    }

    fun ApplicationCall.getOAuthTokens(appname: String, crypter: Crypter?): CookieTokens? {
        val cookieValue = this.getCookie(cookieName(appname)) ?: return null
        return (crypter?.decrypt(cookieValue) ?: Result.success(cookieValue))
            .map { Json.decodeFromString<CookieTokens>(it) }
            .onFailure { log.error("Could not decrypt cookie", it) }
            .getOrNull()
    }
}
