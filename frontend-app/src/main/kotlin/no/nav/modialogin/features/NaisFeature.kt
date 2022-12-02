package no.nav.modialogin.features

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.Clock
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import no.nav.modialogin.utils.NaisState

object Metrics {
    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT, CollectorRegistry.defaultRegistry, Clock.SYSTEM)
}

fun Application.installNaisFeature(
    appname: String,
    appversion: String,
    config: NaisState,
    selftestAttributes: Map<String, Any> = emptyMap()
) {
    install(MicrometerMetrics) {
        distinctNotRegisteredRoutes = false
        registry = Metrics.registry
    }
    val selftestContent: String = buildString {
        appendLine("Application: $appname")
        appendLine("Version: $appversion")
        appendLine()
        for ((key, value) in selftestAttributes.entries) {
            appendLine("$key: $value")
        }
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
                    call.respondText(selftestContent)
                }
                get("metrics") {
                    call.respondText(Metrics.registry.scrape())
                }
            }
        }
    }
}
