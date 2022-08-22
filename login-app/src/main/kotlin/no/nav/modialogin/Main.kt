package no.nav.modialogin

import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import no.nav.modialogin.common.KotlinUtils
import no.nav.modialogin.common.KtorServer.server
import no.nav.modialogin.common.features.DefaultFeatures
import no.nav.modialogin.common.features.DefaultFeatures.installDefaultFeatures
import no.nav.modialogin.common.features.installNaisFeature
import no.nav.modialogin.features.loginflowfeature.LoginFlowFeature
import no.nav.modialogin.features.loginflowfeature.LoginFlowFeature.Companion.installLoginFlowFeature

fun main() {
    startApplication()
}

fun startApplication() {
    val outsideDocker = KotlinUtils.getProperty("OUTSIDE_DOCKER") == "true"
    val config = LoginAppConfig()
    val port = if (outsideDocker) config.exposedPort else 8080
    server(port) { naisState ->
        install(StatusPages, DefaultFeatures.statusPageConfig)
        installDefaultFeatures()
        installNaisFeature(
            config.appName, config.appVersion, naisState,
            buildMap {
                put("ISSO_AUTH_PROVIDER", true)
                put("ISSO_CLIENT_ID", config.idpClientId)
                put("ISSO_WELL_KNOWN_URL", config.idpDiscoveryUrl)
            }
        )
        installLoginFlowFeature(
            LoginFlowFeature.Config(
                appname = config.appName,
                idpDiscoveryUrl = config.idpDiscoveryUrl,
                idpClientId = config.idpClientId,
                idpClientSecret = config.idpClientSecret,
                authTokenResolver = config.authTokenResolver,
                refreshTokenResolver = config.refreshTokenResolver,
                exposedPort = config.exposedPort
            )
        )
    }
}
