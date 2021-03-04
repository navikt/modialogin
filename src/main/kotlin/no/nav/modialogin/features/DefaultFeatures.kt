package no.nav.modialogin.features

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import org.slf4j.event.Level

fun Application.installDefaultFeatures(skipStatusPages: Boolean = false) {
    if (!skipStatusPages) {
        install(StatusPages) {
            exception<Throwable> {
                call.respond(HttpStatusCode.InternalServerError, it.message ?: it.localizedMessage)
                throw it
            }
        }
    }

    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().contains("/internal/").not() }
    }
}
