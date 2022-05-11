package no.nav.modialogin.common.features

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.slf4j.event.Level

object DefaultFeatures {
    val statusPageConfig: StatusPagesConfig.() -> Unit = {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, cause.message ?: cause.localizedMessage)
            throw cause
        }
    }

    fun Application.installDefaultFeatures() {
        install(ContentNegotiation) {
            json()
        }
        install(XForwardedHeaders) {
            hostHeaders.clear()
            forHeaders.clear()
        }
        install(CallLogging) {
            level = Level.INFO
            filter { call -> call.request.path().contains("/internal/").not() }
        }
    }
}
