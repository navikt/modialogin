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
import no.nav.modialogin.common.FileUtils
import no.nav.modialogin.common.KtorUtils
import no.nav.modialogin.common.Templating
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
            val rootFolder = File(config.rootFolder)
            val tmplFolder = File("/tmp/www")
            FileUtils.copyAndProcessFiles(rootFolder, tmplFolder) {
                Templating.replaceVariableReferences(it, null)
            }

            install(IgnoreTrailingSlash)
            install(StatusPages) {
                status(HttpStatusCode.Unauthorized) { call, _ ->
                    val port = if (config.xForwardedPort == 8080) "" else ":${config.xForwardedPort}"
                    val originalUri = KtorUtils.encode("${call.request.origin.scheme}://${call.request.host()}$port${call.request.uri}")
                    call.respondRedirect("${config.startLoginUrl}?url=$originalUri")
                }
                status(HttpStatusCode.NotFound) { call, _ ->
                    if (!call.isRequestForFile()) {
                        call.response.status(HttpStatusCode.OK)
                        call.respondFile(File(tmplFolder, "index.html"))
                    }
                }

                DefaultFeatures.statusPageConfig(this)
            }

            routing {
                authenticate {
                    static(config.appname) {
                        staticRootFolder = File("/tmp/www")
                        files(".")
                        default("index.html")
                    }
                }
            }
        }
    }
}

private val fileRegex = Regex("\\.(?:css|js|jpe?g|gif|ico|png|xml|otf|ttf|eot|woff|svg|map|json)$")
private fun ApplicationCall.isRequestForFile(): Boolean {
    val uri = this.request.uri
    return fileRegex.containsMatchIn(uri)
}
