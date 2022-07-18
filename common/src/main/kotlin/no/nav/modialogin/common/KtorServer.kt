package no.nav.modialogin.common

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.netty.handler.codec.http.HttpObjectDecoder
import io.netty.handler.codec.http.HttpServerCodec
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object KtorServer {
    val log: Logger = LoggerFactory.getLogger("KtorServer")
    val tjenestekallLogger: Logger = LoggerFactory.getLogger("SecureLog")

    fun server(port: Int, module: Application.(NaisState) -> Unit) {
        val naisState = NaisState()
        val server = embeddedServer(Netty, port, configure = {
            this.httpServerCodec = {
                /**
                 * Netty har som default 8kb maks på headere, med mange og store cookies kan man overskride dette.
                 * Vi dobler derfor til 16kb
                 */
                HttpServerCodec(
                    HttpObjectDecoder.DEFAULT_MAX_INITIAL_LINE_LENGTH,
                    HttpObjectDecoder.DEFAULT_MAX_HEADER_SIZE * 2,
                    HttpObjectDecoder.DEFAULT_MAX_CHUNK_SIZE,
                )
            }
        }) {
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
}
