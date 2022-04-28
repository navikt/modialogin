package no.nav.modialogin

import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import no.nav.modialogin.common.AppState
import no.nav.modialogin.common.KtorServer.server
import no.nav.modialogin.common.features.DefaultFeatures
import no.nav.modialogin.common.features.DefaultFeatures.installDefaultFeatures
import no.nav.modialogin.common.features.LoginFlowFeature
import no.nav.modialogin.common.features.LoginFlowFeature.Companion.installLoginFlowFeature
import no.nav.modialogin.common.features.installNaisFeature

fun main() {
    startApplication()
}

fun startApplication() {
    val appConfig = LoginAppConfig()
    val port = if (appConfig.dockerCompose) 8080 else appConfig.exposedPort
    server(port) { naisState ->
        val config = AppState(naisState, appConfig)
        install(StatusPages, DefaultFeatures.statusPageConfig)
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
                exposedPort = config.config.exposedPort
            )
        )
    }
}
