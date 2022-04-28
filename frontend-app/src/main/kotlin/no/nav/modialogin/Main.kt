package no.nav.modialogin

import io.ktor.application.*
import io.ktor.features.*
import no.nav.modialogin.common.AppState
import no.nav.modialogin.common.KtorServer.log
import no.nav.modialogin.common.KtorServer.server
import no.nav.modialogin.common.Oidc
import no.nav.modialogin.common.features.AuthFeature
import no.nav.modialogin.common.features.AuthFeature.Companion.installAuthFeature
import no.nav.modialogin.common.features.CSPFeature.applyCSPFeature
import no.nav.modialogin.common.features.DefaultFeatures.installDefaultFeatures
import no.nav.modialogin.common.features.HostStaticFilesFeature
import no.nav.modialogin.common.features.HostStaticFilesFeature.Companion.installHostStaticFilesFeature
import no.nav.modialogin.common.features.ReferrerPolicyFeature.applyReferrerPolicyFeature
import no.nav.modialogin.common.features.installNaisFeature

fun main() {
    startApplication()
}

fun startApplication() {
    val appConfig = FrontendAppConfig()
    val port = if (appConfig.dockerCompose) 8080 else appConfig.exposedPort
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
                rootFolder = if (port == 8080) "/" else "./frontend-app"
            )
        )
    }
}
