package no.nav.modialogin.common.features

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.modialogin.common.NaisState

object Metrics {
    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
}
fun Application.installNaisFeature(appname: String, appversion: String, config: NaisState) {
    install(MicrometerMetrics) {
        registry = Metrics.registry
    }

    routing {
        route(appname) {
            route("internal") {
                get("isAlive") {
                    if (config.isAlive) {
                        call.respondText("Alive")
                    } else {
                        call.respondText("Not alive", status = HttpStatusCode.InternalServerError)
                    }
                }
                get("isReady") {
                    if (config.isReady) {
                        call.respondText("Ready")
                    } else {
                        call.respondText("Not ready", status = HttpStatusCode.InternalServerError)
                    }
                }
                get("selftest") {
                    call.respondText("Application: $appname\nVersion: $appversion")
                }
                get("metrics") {
                    call.respond(Metrics.registry.scrape())
                }
            }
        }
    }
}
