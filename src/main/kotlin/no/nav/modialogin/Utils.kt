package no.nav.modialogin

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8

object Utils {
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
                domain = domain,
                path = path,
                maxAge = maxAgeInSeconds,
                secure = false, // HTTPS håndteres av trafik
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
            domain = domain,
            path = path
        )
    }

    fun encode(value: String) = URLEncoder.encode(value, UTF_8)
    fun decode(value: String) = URLDecoder.decode(value, UTF_8)
}
