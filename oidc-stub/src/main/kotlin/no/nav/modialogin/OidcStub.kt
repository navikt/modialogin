package no.nav.modialogin

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*

fun main() {
    startApplication()
}

fun startApplication() {
    val outsideDocker = System.getProperty("OUTSIDE_DOCKER") == "true"

    embeddedServer(Netty, 8080) {
        install(ContentNegotiation) {
            json()
        }

        oidc(
            route = "azuread",
            issuer = "azuread",
            outsideDocker = outsideDocker,
            supportOnBehalfOf = true
        )

        unleash(
            "feature1" to true,
            "feature2" to false,
            "feature3" to true
        )
        redisIntrospection(
            if (outsideDocker) { "localhost" }
            else { "redis" }
        )
    }.start(wait = true)
}
