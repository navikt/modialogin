package no.nav.modialogin.features

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.serialization.*
import org.slf4j.event.Level

object DefaultFeatures {
    fun Application.installDefaultFeatures() {
        install(StatusPages) {
            exception<Throwable> {
                call.respond(HttpStatusCode.InternalServerError, it.message ?: it.localizedMessage)
                throw it
            }
        }
        install(ContentNegotiation) {
            json()
        }
        install(XForwardedHeaderSupport) {
            // These change the request.host which makes the redirect fail
            hostHeaders.clear()
            forHeaders.clear()
        }
        install(CallLogging) {
            level = Level.INFO
            filter { call -> call.request.path().contains("/internal/").not() }
        }
    }
}
