package no.nav.modialogin.common.features

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.serialization.*
import org.slf4j.event.Level

object DefaultFeatures {
    val statusPageConfig: StatusPages.Configuration.() -> Unit = {
        exception<Throwable> {
            call.respond(HttpStatusCode.InternalServerError, it.message ?: it.localizedMessage)
            throw it
        }
    }

    fun Application.installDefaultFeatures() {
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
