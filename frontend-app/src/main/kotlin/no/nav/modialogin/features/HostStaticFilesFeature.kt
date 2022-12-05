package no.nav.modialogin.features

import io.getunleash.Unleash
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import no.nav.modialogin.utils.Templating
import no.nav.modialogin.utils.TemplatingEngine
import no.nav.modialogin.features.csp.CSPNonceSource
import no.nav.modialogin.utils.UnleashTemplateSource

class StaticFileHostingConfig(
    var contextpath: String = "",
    var rootFolder: String = "/",
    var unleash: Unleash? = null
)
val StaticFileHosting = createApplicationPlugin("static-file-hosting", ::StaticFileHostingConfig) {
    val context = pluginConfig.contextpath
    val rootFolder = pluginConfig.rootFolder
    val unleash = pluginConfig.unleash

    val templateSources = listOfNotNull(
        Templating.EnvSource,
        CSPNonceSource,
        if (unleash != null) UnleashTemplateSource.create(unleash) else null
    ).toTypedArray()
    val templateEngine = TemplatingEngine(*templateSources)

    with(application) {
        install(IgnoreTrailingSlash)
        install(TemplatingFeature.Plugin) {
            templatingEngine = templateEngine
        }
        routing {
            route(context) {
                authenticate {
                    install(TemplatingFeature.EnableRouteTransform)
                    singlePageApplication {
                        react(rootFolder)
                    }
                }
            }
        }
    }
}