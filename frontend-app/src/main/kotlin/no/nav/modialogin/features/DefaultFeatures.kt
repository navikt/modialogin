package no.nav.modialogin.features

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import no.nav.modialogin.Logging
import no.nav.modialogin.utils.KotlinUtils.callIdProperty
import org.slf4j.event.Level
import java.util.*

class DefaultFeaturesConfig {
    var referrerPolicy: String? = null
    fun referrerPolicy(policy: String) {
        referrerPolicy = policy
    }
}
val DefaultFeatures = createApplicationPlugin("default", ::DefaultFeaturesConfig) {
    val referrerPolicy = pluginConfig.referrerPolicy

    with(application) {
        install(ContentNegotiation) {
            json()
        }

        install(XForwardedHeaders) {
            hostHeaders.clear()
            forHeaders.clear()
        }

        install(CallId) {
            header(HttpHeaders.XCorrelationId)
            verify { it.isNotEmpty() }
            generate { UUID.randomUUID().toString() }
        }

        install(CallLogging) {
            level = Level.INFO
            format(::logFormat)
            filter { call -> call.request.path().contains("/internal/").not() }
            callIdMdc(callIdProperty)
        }

        install(StatusPages) {
            exception<Throwable> { call, cause ->
                val message = cause.message ?: cause.localizedMessage
                Logging.log.error("Unhandled exception:", cause)
                call.respond(HttpStatusCode.InternalServerError, message)
                throw cause
            }
        }

        if (referrerPolicy != null) {
            install(DefaultHeaders) {
                header("Referrer-Policy", referrerPolicy)
            }
        }
    }
}
private fun logFormat(call: ApplicationCall): String {
    return when (val status = call.response.status()) {
        HttpStatusCode.Found -> "$status: ${call.request.httpMethod.value} - ${call.request.path()} -> ${call.response.headers[HttpHeaders.Location]}"
        else -> "$status: ${call.request.httpMethod.value} - ${call.request.path()}"
    }
}