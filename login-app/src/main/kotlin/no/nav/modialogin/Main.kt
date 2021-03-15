package no.nav.modialogin

import io.ktor.application.*
import io.ktor.features.*
import no.nav.modialogin.features.LoginFlowFeature
import no.nav.modialogin.features.DefaultFeatures
import no.nav.modialogin.features.DefaultFeatures.installDefaultFeatures
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

        install(StatusPages, DefaultFeatures.statusPageConfig)
        installDefaultFeatures()
        installNaisFeature(config.config.appname, config.nais)
        installLoginFlowFeature(
            LoginFlowFeature.Config(
                appname = config.config.appname,
                idpDiscoveryUrl = config.config.idpDiscoveryUrl,
                idpClientId = config.config.idpClientId,
                idpClientSecret = config.config.idpClientSecret,
                xForwardingPort = config.config.xForwardingPort
            )
        )
    }
}
