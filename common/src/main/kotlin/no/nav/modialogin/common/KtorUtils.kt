package no.nav.modialogin.common

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import no.nav.modialogin.common.KotlinUtils.indicesOf
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8

object KtorUtils {
    fun ApplicationCall.respondWithCookie(
        name: String,
        value: String,
        domain: String = request.host(),
        path: String = "/",
        maxAgeInSeconds: Int = 3600,
        crypter: Crypter? = null
    ) {
        val cookieValue = crypter?.encrypt(value) ?: value
        this.response.cookies.append(
            Cookie(
                name = name,
                value = cookieValue,
                domain = cookieDomain(domain),
                path = path,
                maxAge = maxAgeInSeconds,
                encoding = CookieEncoding.BASE64_ENCODING,
                secure = false,
                httpOnly = true
            )
        )
    }

    fun ApplicationCall.removeCookie(
        name: String,
        domain: String = request.host(),
        path: String = "/"
    ) {
        this.response.cookies.appendExpired(
            name = name,
            domain = cookieDomain(domain),
            path = path
        )
    }

    fun ApplicationCall.getCookie(name: String, crypter: Crypter? = null): String? {
        val raw = this.request.cookies[name, CookieEncoding.BASE64_ENCODING]
        if (crypter != null && raw != null) {
            return crypter.decrypt(raw)
        }
        return raw
    }

    fun encode(value: String): String = URLEncoder.encode(value, UTF_8)
    fun decode(value: String): String = URLDecoder.decode(value, UTF_8)

    private fun cookieDomain(host: String): String {
        val indices = host.indicesOf(".")
        if (indices.size < 2) {
            return host
        }
        return host.substring(indices.first() + 1)
    }
}
