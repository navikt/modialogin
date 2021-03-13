package no.nav.modialogin

import no.nav.modialogin.common.AppState
import no.nav.modialogin.common.KtorUtils.server
import no.nav.modialogin.common.Oidc
import no.nav.modialogin.common.features.AuthFeature
import no.nav.modialogin.common.features.AuthFeature.Companion.installAuthFeature
import no.nav.modialogin.common.features.DefaultFeatures.installDefaultFeatures
import no.nav.modialogin.common.features.HostStaticFilesFeature
import no.nav.modialogin.common.features.HostStaticFilesFeature.Companion.installHostStaticFilesFeature
import no.nav.modialogin.common.features.installNaisFeature

fun main() {
    startApplication()
}

fun startApplication() {
    val appConfig = FrontendAppConfig()
    val port = if (appConfig.dockerCompose) 8080 else appConfig.xForwardingPort
    server(port) { naisState ->
        val config = AppState(naisState, appConfig)
        val oidcClient = Oidc.JwksClient(
            Oidc.JwksClientConfig(
                discoveryUrl = config.config.idpDiscoveryUrl
            )
        )

        installDefaultFeatures()
        installNaisFeature(config.config.appname, config.nais)
        installAuthFeature(
            AuthFeature.Config(
                jwksUrl = oidcClient.jwksConfig.jwksUrl,
                acceptedAudience = config.config.idpClientId
            )
        )
        installHostStaticFilesFeature(
            HostStaticFilesFeature.Config(
                appname = config.config.appname,
                xForwardedPort = config.config.xForwardingPort,
                startLoginUrl = config.config.delegatedLoginUrl
            )
        )
    }
}
