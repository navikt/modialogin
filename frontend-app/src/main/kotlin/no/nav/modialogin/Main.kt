package no.nav.modialogin

import io.ktor.server.application.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.request.*
import no.nav.modialogin.common.KotlinUtils.getProperty
import no.nav.modialogin.common.KtorServer.log
import no.nav.modialogin.common.KtorServer.server
import no.nav.modialogin.common.features.DefaultFeatures.installDefaultFeatures
import no.nav.modialogin.common.features.installNaisFeature
import no.nav.modialogin.features.CSPFeature
import no.nav.modialogin.features.HostStaticFilesFeature
import no.nav.modialogin.features.HostStaticFilesFeature.Companion.installHostStaticFilesFeature
import no.nav.modialogin.features.ReferrerPolicyFeature.applyReferrerPolicyFeature
import no.nav.modialogin.features.authfeature.*
import no.nav.modialogin.features.bffproxyfeature.BFFProxyFeature
import no.nav.modialogin.features.bffproxyfeature.BFFProxyFeature.installBFFProxy

fun main() {
    startApplication()
}

fun startApplication() {
    val config = FrontendAppConfig()
    val staticFilesRootFolder = if (config.appMode.withinDockercompose) "/www" else "./frontend-app/www"

    val port = config.appMode.appport()
    log.info("Starting app: $port")

    server(port) { naisState ->
        install(SessionOauthFeature) {
            appmode = config.appMode
            appname = config.appName
            oidcConfig = requireNotNull(config.azureAd).toOidcClientConfig()
            redisConfig = config.redis
            skipWhen = { call ->
                val url = call.request.uri
                val isInternal = url.contains("/${config.appName}/internal/")
                val isWhoamiI = url.endsWith("/${config.appName}/internal/whoami")
                isInternal && !isWhoamiI
            }
        }
        installDefaultFeatures()
        install(DefaultHeaders) {
            applyReferrerPolicyFeature(config.referrerPolicy)
        }
        install(CSPFeature.Plugin) {
            reportOnly = config.cspReportOnly
            directive = config.cspDirectives
        }
        installNaisFeature(
            config.appName, config.appVersion, naisState,
            buildMap {
                put("AZURE_APP_CLIENT_ID", config.azureAd.clientId)
                put("AZURE_APP_TENANT_ID", config.azureAd.tenantId)
                put("AZURE_APP_WELL_KNOWN_URL", config.azureAd.wellKnownUrl)
            }
        )
        installHostStaticFilesFeature(
            HostStaticFilesFeature.Config(
                appname = config.appName,
                rootFolder = staticFilesRootFolder,
                unleash = config.unleash
            )
        )
        installBFFProxy(
            BFFProxyFeature.Config(
                appName = config.appName,
                proxyConfig = config.proxyConfig
            )
        )
    }
}
