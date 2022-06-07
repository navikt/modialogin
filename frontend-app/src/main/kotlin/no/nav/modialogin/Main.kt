package no.nav.modialogin

import io.ktor.server.application.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.request.*
import no.nav.modialogin.auth.AzureAdConfig
import no.nav.modialogin.auth.OidcClient
import no.nav.modialogin.common.AppState
import no.nav.modialogin.common.KotlinUtils.getProperty
import no.nav.modialogin.common.KtorServer.log
import no.nav.modialogin.common.KtorServer.server
import no.nav.modialogin.common.features.DefaultFeatures.installDefaultFeatures
import no.nav.modialogin.common.features.installNaisFeature
import no.nav.modialogin.features.CSPFeature.applyCSPFeature
import no.nav.modialogin.features.HostStaticFilesFeature
import no.nav.modialogin.features.HostStaticFilesFeature.Companion.installHostStaticFilesFeature
import no.nav.modialogin.features.ReferrerPolicyFeature.applyReferrerPolicyFeature
import no.nav.modialogin.features.authfeature.*
import no.nav.modialogin.features.bffproxyfeature.BFFProxyFeature
import no.nav.modialogin.features.bffproxyfeature.BFFProxyFeature.installBFFProxy
import no.nav.modialogin.features.oauthfeature.OauthFeature
import no.nav.modialogin.features.oauthfeature.OauthFeature.Companion.installOAuthRoutes
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
        val azureAdConfig = AzureAdConfig.load()

        install(AuthFilterFeature) {
            ignorePattern = { call ->
                val url = call.request.uri
                url.contains("/internal/") || url.contains("/${appConfig.appName}/oauth2/")
            }
            register(
                DelegatedAuthProvider(
                    name = OpenAmAuthProvider,
                    xForwardedPort = config.config.exposedPort,
                    startLoginUrl = config.config.delegatedLoginUrl,
                    refreshUrl = config.config.delegatedRefreshUrl,
                    authTokenResolver = config.config.authTokenResolver,
                    refreshTokenResolver = config.config.refreshTokenResolver,
                    acceptedAudience = config.config.idpClientId,
                    acceptedIssuer = "openam"
                )
            )
            azureAdConfig?.let { it ->
                log.info("Registering azure ad provider")
                register(
                    OidcAuthProvider(
                        name = AzureAdAuthProvider,
                        appname = appConfig.appName,
                        xForwardedPort = config.config.exposedPort,
                        authTokenResolver = "AAD_modia_access_token",
                        refreshTokenResolver = "AAD_modia_refresh_token",
                        config = it
                    )
                )
            }
        }
        azureAdConfig?.let {
            installOAuthRoutes(
                OauthFeature.Config(
                    appname = appConfig.appName,
                    oidc = OidcClient(it.toOidcClientConfig()),
                    authTokenResolver = "AAD_modia_access_token",
                    refreshTokenResolver = "AAD_modia_refresh_token",
                    exposedPort = config.config.exposedPort
                )
            )
        }
        installDefaultFeatures()
        install(DefaultHeaders) {
            applyCSPFeature(appConfig.cspReportOnly, appConfig.cspDirectives)
            applyReferrerPolicyFeature(appConfig.referrerPolicy)
        }
        installNaisFeature(config.config.appName, config.config.appVersion, config.nais)
        installHostStaticFilesFeature(
            HostStaticFilesFeature.Config(
                appname = config.config.appName,
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
