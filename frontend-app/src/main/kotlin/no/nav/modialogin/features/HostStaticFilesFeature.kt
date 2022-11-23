package no.nav.modialogin.features

import io.getunleash.Unleash
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.modialogin.common.FileUtils.fileRegex
import no.nav.modialogin.common.Templating
import no.nav.modialogin.common.TemplatingEngine
import no.nav.modialogin.common.features.DefaultFeatures
import no.nav.modialogin.features.templatingfeature.TemplatingFeature
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
        val unleash: Unleash? = null
    )

    fun install(application: Application) {
        val templateSources = listOfNotNull(
            Templating.EnvSource,
            if (config.unleash != null) UnleashTemplateSource.create(config.unleash) else null
        ).toTypedArray()
        val templateEngine = TemplatingEngine(*templateSources)

        with(application) {
            val rootFolder = File(config.rootFolder)
            val tmpFolder = File("/tmp/www")
            rootFolder.copyRecursively(target = tmpFolder, overwrite = true)

            install(IgnoreTrailingSlash)
            install(StatusPages) {
                status(HttpStatusCode.NotFound) { call, _ ->
                    if (!call.isRequestForFile()) {
                        call.response.status(HttpStatusCode.OK)
                        call.respondFile(File(tmpFolder, "index.html"))
                    }
                }

                DefaultFeatures.statusPageConfig(this)
            }

            install(TemplatingFeature.Plugin) {
                templatingEngine = templateEngine
            }
            routing {
                authenticate {
                    static(config.appname) {
                        install(TemplatingFeature.EnableRouteTransform)
                        staticRootFolder = File("/tmp/www")
                        files(".")
                        default("index.html")
                    }
                }
            }
        }
    }
    private fun ApplicationCall.isRequestForFile(): Boolean {
        val uri = this.request.uri
        return fileRegex.containsMatchIn(uri)
    }
}
