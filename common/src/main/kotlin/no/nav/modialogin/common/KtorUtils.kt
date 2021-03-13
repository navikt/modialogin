package no.nav.modialogin.common

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8

object KtorUtils {
    private val log: Logger = LoggerFactory.getLogger("KtorServer")

    fun server(port: Int, module: Application.(NaisState) -> Unit) {
        val naisState = NaisState()
        val server = embeddedServer(Netty, port) {
            module(naisState)
            naisState.isReady = true
        }
        Runtime.getRuntime().addShutdownHook(
            Thread {
                log.info("Shutting down...")
                naisState.isReady = false
                server.stop(500, 500)
            }
        )
        server.start(wait = true)
    }

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
            domain = domain,
            path = path
        )
    }

    fun encode(value: String) = URLEncoder.encode(value, UTF_8)
    fun decode(value: String) = URLDecoder.decode(value, UTF_8)
}
