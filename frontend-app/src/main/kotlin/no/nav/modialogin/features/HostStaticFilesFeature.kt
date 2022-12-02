package no.nav.modialogin.features

import io.getunleash.Unleash
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import no.nav.modialogin.utils.Templating
import no.nav.modialogin.utils.TemplatingEngine
import no.nav.modialogin.features.csp.CSPFeature
import no.nav.modialogin.features.templatingfeature.TemplatingFeature
import no.nav.modialogin.utils.UnleashTemplateSource

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
            CSPFeature.NonceSource,
            if (config.unleash != null) UnleashTemplateSource.create(config.unleash) else null
        ).toTypedArray()
        val templateEngine = TemplatingEngine(*templateSources)

        with(application) {
            install(IgnoreTrailingSlash)
            install(TemplatingFeature.Plugin) {
                templatingEngine = templateEngine
            }
            routing {
                route(config.appname) {
                    authenticate {
                        install(TemplatingFeature.EnableRouteTransform)
                        singlePageApplication {
                            react(config.rootFolder)
                        }
                    }
                }
            }
        }
    }
}
