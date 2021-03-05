package no.nav.modialogin

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.modialogin.features.AuthFeature.installAuthFeature
import no.nav.modialogin.features.LoginFeature.installLoginFeature
import no.nav.modialogin.features.hostStaticFiles
import no.nav.modialogin.features.installDefaultFeatures
import no.nav.modialogin.features.installNaisFeature
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("modialogin")
fun startApplication() {
    val config = Config.create()
    val server = embeddedServer(Netty, 8080) {
        installDefaultFeatures(skipStatusPages = config.env.hostStaticFiles)
        installAuthFeature {
            this.jwksUrl = config.oidc.config.jwksUrl
            this.acceptedAudience = config.env.idpClientId
        }
        installNaisFeature(config)
        installLoginFeature(config)

        if (config.env.hostStaticFiles) {
            hostStaticFiles(config)
        }
    }
    config.state.isReady = true

    Runtime.getRuntime().addShutdownHook(
        Thread {
            log.info("Shutting down...")
            config.state.isReady = false
            server.stop(500, 500)
        }
    )

    server.start(wait = true)
}
