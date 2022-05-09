package no.nav.modialogin

import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import no.nav.modialogin.common.AppState
import no.nav.modialogin.common.KotlinUtils
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
    val outsideDocker = KotlinUtils.getProperty("OUTSIDE_DOCKER") == "true"
    val appConfig = LoginAppConfig()
    val port = if (outsideDocker) appConfig.exposedPort else 8080
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
