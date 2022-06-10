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
        maxAgeInSeconds: Int = 3600
    ) {
        this.response.cookies.append(
            Cookie(
                name = name,
                value = value,
                domain = cookieDomain(domain),
                path = path,
                maxAge = maxAgeInSeconds,
                encoding = CookieEncoding.RAW,
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

    fun encode(value: String) = URLEncoder.encode(value, UTF_8)
    fun decode(value: String) = URLDecoder.decode(value, UTF_8)

    fun cookieDomain(host: String): String {
        val indices = host.indicesOf(".")
        if (indices.size < 2) {
            return host
        }
        return host.substring(indices.first() + 1)
    }
}