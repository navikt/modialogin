package no.nav.modialogin.common.features

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.modialogin.common.NaisState

object Metrics {
    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
}
interface WhoAmIPrincipal : Principal {
    val description: String
}

fun Application.installNaisFeature(appname: String, appversion: String, config: NaisState, selftestAttributes: Map<String, Any> = emptyMap()) {
    install(MicrometerMetrics) {
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
                    call.respond(Metrics.registry.scrape())
                }

                get("whoami") {
                    call.respondText(call.principal<WhoAmIPrincipal>()?.description ?: "Unknown")
                }
            }
        }
    }
}
