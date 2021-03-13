package no.nav.modialogin.common.features

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.modialogin.common.KtorUtils
import java.io.File

class HostStaticFilesFeature(val config: Config) {
    companion object {
        fun Application.installHostStaticFilesFeature(config: Config) {
            HostStaticFilesFeature(config).install(this)
        }
    }
    class Config(
        val appname: String,
        val startLoginUrl: String,
        val xForwardedPort: Int
    )
    fun install(application: Application) {
        with(application) {
            install(StatusPages) {
                status(HttpStatusCode.Unauthorized) {
                    val port = if (config.xForwardedPort == 8080) "" else ":${config.xForwardedPort}"
                    val originalUri = KtorUtils.encode("${call.request.origin.scheme}://${call.request.host()}$port${call.request.uri}")
                    call.respondRedirect("${config.startLoginUrl}?url=$originalUri")
                }

                DefaultFeatures.statusPageConfig(this)
            }

            routing {
                trailingSlashRoute(config.appname) {
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
    }

    private fun Route.trailingSlashRoute(path: String, build: Route.() -> Unit): Route {
        route(path, build)
        return route("$path/", build)
    }
}
