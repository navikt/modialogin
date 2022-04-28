package no.nav.modialogin.common.features

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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
        val xForwardedPort: Int,
        val rootFolder: String = "/"
    )
    fun install(application: Application) {
        with(application) {
            install(StatusPages) {
                status(HttpStatusCode.Unauthorized) { call, _ ->
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
                            staticRootFolder = File(config.rootFolder)
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
