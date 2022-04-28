package no.nav.modialogin.common

import io.ktor.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object KtorServer {
    val log: Logger = LoggerFactory.getLogger("KtorServer")

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
}
