package no.nav.modialogin.features

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.modialogin.common.FileUtils
import no.nav.modialogin.common.Templating
import no.nav.modialogin.common.TemplatingEngine
import no.nav.modialogin.common.features.DefaultFeatures
import no.nav.modialogin.common.unleash.UnleashService
import no.nav.modialogin.features.unleashtemplatefeature.UnleashTemplateFeature
import java.io.File

class HostStaticFilesFeature(val config: Config) {
    companion object {
        fun Application.installHostStaticFilesFeature(config: Config) {
            HostStaticFilesFeature(config).install(this)
        }
    }

    class Config(
        val appname: String,
        val rootFolder: String = "/",
        val unleashService: UnleashService? = null
    )

    fun install(application: Application) {
        val templateEngine = TemplatingEngine(Templating.EnvSource)

        with(application) {
            val rootFolder = File(config.rootFolder)
            val tmplFolder = File("/tmp/www")
            FileUtils.copyAndProcessFiles(rootFolder, tmplFolder) {
                templateEngine.replaceVariableReferences(null, it)
            }

            install(IgnoreTrailingSlash)
            install(StatusPages) {
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
                        if (config.unleashService != null) {
                            install(UnleashTemplateFeature) {
                                contextpath = config.appname
                                unleashService = config.unleashService
                            }
                        }
                    }
                }
            }
        }
    }
}

private val fileRegex = Regex("\\.(?:css|js|jpe?g|gif|ico|png|xml|otf|ttf|eot|woff|svg|map|json)$")
fun ApplicationCall.isRequestForFile(): Boolean {
    val uri = this.request.uri
    return fileRegex.containsMatchIn(uri)
}