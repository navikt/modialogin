package no.nav.modialogin

import io.ktor.server.application.*
import io.ktor.server.plugins.defaultheaders.*
import no.nav.modialogin.common.AppState
import no.nav.modialogin.common.KotlinUtils.getProperty
import no.nav.modialogin.common.KtorServer.log
import no.nav.modialogin.common.KtorServer.server
import no.nav.modialogin.common.Oidc
import no.nav.modialogin.common.features.CSPFeature.applyCSPFeature
import no.nav.modialogin.common.features.DefaultFeatures.installDefaultFeatures
import no.nav.modialogin.common.features.HostStaticFilesFeature
import no.nav.modialogin.common.features.HostStaticFilesFeature.Companion.installHostStaticFilesFeature
import no.nav.modialogin.common.features.ReferrerPolicyFeature.applyReferrerPolicyFeature
import no.nav.modialogin.common.features.authfeature.AuthFeature
import no.nav.modialogin.common.features.authfeature.AuthFeature.Companion.installAuthFeature
import no.nav.modialogin.common.features.bffproxyfeature.BFFProxyFeature
import no.nav.modialogin.common.features.bffproxyfeature.BFFProxyFeature.installBFFProxy
import no.nav.modialogin.common.features.installNaisFeature
import java.io.File

fun main() {
    startApplication()
}

fun startApplication() {
    val outsideDocker = getProperty("OUTSIDE_DOCKER") == "true"
    val proxyConfigFile = if (outsideDocker) "./frontend-app/proxy-config/proxy-config.json" else "/proxy-config.json"
    val staticFilesRootFolder = if (outsideDocker) "./frontend-app/www" else "/www"

    val appConfig = FrontendAppConfig(File(proxyConfigFile))
    val port = if (outsideDocker) appConfig.exposedPort else 8080
    log.info("Starting app: $port")

    server(port) { naisState ->
        val config = AppState(naisState, appConfig)
        val oidcClient = Oidc.JwksClient(
            Oidc.JwksClientConfig(
                discoveryUrl = config.config.idpDiscoveryUrl
            )
        )

        installDefaultFeatures()
        install(DefaultHeaders) {
            applyCSPFeature(appConfig.cspReportOnly, appConfig.cspDirectives)
            applyReferrerPolicyFeature(appConfig.referrerPolicy)
        }
        installNaisFeature(config.config.appName, config.config.appVersion, config.nais)
        installAuthFeature(
            AuthFeature.Config(
                jwksUrl = oidcClient.jwksConfig.jwksUrl,
                acceptedAudience = config.config.idpClientId,
                authTokenResolver = config.config.authTokenResolver
            )
        )

        installHostStaticFilesFeature(
            HostStaticFilesFeature.Config(
                appname = config.config.appName,
                xForwardedPort = config.config.exposedPort,
                startLoginUrl = config.config.delegatedLoginUrl,
                rootFolder = staticFilesRootFolder
            )
        )
        installBFFProxy(
            BFFProxyFeature.Config(
                appName = appConfig.appName,
                proxyConfig = appConfig.proxyConfig
            )
        )
    }
}
