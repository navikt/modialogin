package no.nav.modialogin.features

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.modialogin.Config

fun Application.installNaisFeature(config: Config) {
    routing {
        route(config.env.appname) {
            route("internal") {
                get("isAlive") {
                    if (config.state.isAlive) {
                        call.respondText("Alive")
                    } else {
                        call.respondText("Not alive", status = HttpStatusCode.InternalServerError)
                    }
                }
                get("isReady") {
                    if (config.state.isReady) {
                        call.respondText("Ready")
                    } else {
                        call.respondText("Not ready", status = HttpStatusCode.InternalServerError)
                    }
                }
            }
        }
    }
}
