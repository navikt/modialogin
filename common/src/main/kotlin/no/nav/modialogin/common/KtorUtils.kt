package no.nav.modialogin.common

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import no.nav.modialogin.common.KotlinUtils.getProperty
import no.nav.modialogin.common.KotlinUtils.indicesOf
import no.nav.personoversikt.crypto.Crypter
import org.slf4j.LoggerFactory
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8

object KtorUtils {
    private val log = LoggerFactory.getLogger("KtorUtils")
    private val isLocalhostDevelopment = getProperty("IS_LOCALHOST_DEV")?.toBoolean() ?: false
    fun ApplicationCall.respondWithCookie(
        name: String,
        value: String,
        domain: String = request.host(),
        path: String = "/",
        maxAgeInSeconds: Int = 3600,
        crypter: Crypter? = null,
        encoding: CookieEncoding = CookieEncoding.BASE64_ENCODING
    ) {
        val cookieValue = crypter
            ?.encrypt(value)
            ?.onFailure { log.error("Could not encrypt cookie value", it) }
            ?.getOrNull()
            ?: value
        this.response.cookies.append(
            Cookie(
                name = name,
                value = cookieValue,
                domain = cookieDomain(domain),
                path = path,
                maxAge = maxAgeInSeconds,
                encoding = encoding,
                secure = !isLocalhostDevelopment,
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
        val raw = this.getCookieValue(name) ?: return null
        return crypter
            ?.decrypt(raw)
            ?.onFailure { log.error("Could not decrypt cookie", it) }
            ?.getOrNull()
            ?: raw
    }

    fun encode(value: String): String = URLEncoder.encode(value, UTF_8)
    fun decode(value: String): String = URLDecoder.decode(value, UTF_8)

    private fun ApplicationCall.getCookieValue(name: String): String? {
        val value = this.request.cookies[name, CookieEncoding.RAW] ?: return null
        if (Base64CommonCodec.isBase64(value)) {
            return this.request.cookies[name, CookieEncoding.BASE64_ENCODING]
        }
        return value
    }

    private fun cookieDomain(host: String): String {
        val indices = host.indicesOf(".")
        if (indices.size < 2) {
            return host
        }
        return host.substring(indices.first() + 1)
    }
}
