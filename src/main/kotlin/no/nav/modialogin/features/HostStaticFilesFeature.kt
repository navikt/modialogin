package no.nav.modialogin.features

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.modialogin.Config
import no.nav.modialogin.Utils
import no.nav.modialogin.Utils.respondWithCookie
import java.io.File

fun Application.hostStaticFiles(config: Config) {
    install(StatusPages) {
        status(HttpStatusCode.Unauthorized) {
            val stateNounce = LoginFeature.generateStateNounce()
            val port = call.request.port().let { if (it == 80) "" else ":$it" }
            val originalUri = "${call.request.origin.scheme}://${call.request.host()}$port${call.request.uri}"
            call.respondWithCookie(stateNounce, Utils.encode(originalUri))

            val redirectUrl = LoginFeature.callbackUrl(
                authorizationEndpoint = config.oidc.config.authorizationEndpoint,
                clientId = config.env.idpClientId,
                stateNounce = stateNounce,
                callbackUrl = LoginFeature.loginUrl(call.request, config)
            )
            call.respondRedirect(permanent = false, url = redirectUrl)
        }
        exception<Throwable> {
            call.respond(HttpStatusCode.InternalServerError, it.message ?: it.localizedMessage)
            throw it
        }
    }
    routing {
        trailingSlashRoute(config.env.appname) {
            authenticate {
                static {
                    staticRootFolder = File("/")
                    files("app")
                    default("app/index.html")
                }
            }
        }
    }
}

private fun Route.trailingSlashRoute(path: String, build: Route.() -> Unit): Route {
    route(path, build)
    return route("$path/", build)
}
