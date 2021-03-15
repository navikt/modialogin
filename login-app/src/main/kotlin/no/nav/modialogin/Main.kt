package no.nav.modialogin

import no.nav.modialogin.features.DefaultFeatures.installDefaultFeatures
import no.nav.modialogin.features.LoginFlowFeature
import no.nav.modialogin.features.LoginFlowFeature.Companion.installLoginFlowFeature
import no.nav.modialogin.features.installNaisFeature
import no.nav.modialogin.infra.AppState
import no.nav.modialogin.infra.KtorServer.server

fun main() {
    startApplication()
}

fun startApplication() {
    val appConfig = LoginAppConfig()
    val port = if (appConfig.dockerCompose) 8080 else appConfig.xForwardingPort
    server(port) { naisState ->
        val config = AppState(naisState, appConfig)

        installDefaultFeatures()
        installNaisFeature(config.config.appName, config.config.appVersion, config.nais)
        installLoginFlowFeature(
            LoginFlowFeature.Config(
                appname = config.config.appName,
                idpDiscoveryUrl = config.config.idpDiscoveryUrl,
                idpClientId = config.config.idpClientId,
                idpClientSecret = config.config.idpClientSecret,
                authTokenResolver = config.config.authTokenResolver,
                refreshTokenResolver = config.config.refreshTokenResolver,
                xForwardingPort = config.config.xForwardingPort
            )
        )
    }
}
