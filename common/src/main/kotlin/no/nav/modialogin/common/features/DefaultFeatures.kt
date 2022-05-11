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
            format(::logFormat)
            filter { call -> call.request.path().contains("/internal/").not() }
        }
    }

    private fun logFormat(call: ApplicationCall): String {
        val string = when (val status = call.response.status()) {
            HttpStatusCode.Found -> "$status: ${call.request.httpMethod.value} - ${call.request.path()} -> ${call.response.headers[HttpHeaders.Location]}"
            else -> "$status: ${call.request.httpMethod.value} - ${call.request.path()}"
        }
        return string.maskSensitiveInfo()
    }
}
