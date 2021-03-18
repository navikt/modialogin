package no.nav.modialogin.features

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.modialogin.infra.NaisState

fun Application.installNaisFeature(appname: String, appversion: String, config: NaisState) {
    routing {
        route(appname) {
            route("internal") {
                get("isAlive") {
                    if (config.isAlive) {
                        call.respondText("Alive: $appname")
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
            }
        }
    }
}
