package no.nav.modialogin

import io.ktor.server.application.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.request.*
import no.nav.modialogin.auth.OidcClient
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
import no.nav.modialogin.features.oauthfeature.OAuthAuthProvider
import no.nav.modialogin.features.oauthfeature.OAuthFeature
import no.nav.modialogin.features.oauthfeature.OAuthFeature.Companion.installOAuthRoutes

fun main() {
    startApplication()
}

fun startApplication() {
    val outsideDocker = getProperty("OUTSIDE_DOCKER") == "true"
    val staticFilesRootFolder = if (outsideDocker) "./frontend-app/www" else "/www"

    val config = FrontendAppConfig()
    val port = if (outsideDocker) config.exposedPort else 8080
    log.info("Starting app: $port")

    server(port) { naisState ->
        install(AuthFilterFeature) {
            ignorePattern = { call ->
                val url = call.request.uri
                val isInternal = url.contains("/internal/")
                val isWhoamiI = url.endsWith("/whoami")
                val isOauthRoutes = url.contains("/${config.appName}/oauth2/")
                isOauthRoutes || (isInternal && !isWhoamiI)
            }
            config.openAm?.let {
                log.info("Registering openAm provider")
                register(
                    DelegatedAuthProvider(
                        name = OpenAmAuthProvider,
                        xForwardedPort = config.exposedPort,
                        startLoginUrl = config.openAm.loginUrl,
                        refreshUrl = config.openAm.refreshUrl,
                        wellKnownUrl = config.openAm.wellKnownUrl,
                        authTokenResolver = config.openAm.idTokenCookieName,
                        refreshTokenResolver = config.openAm.refreshTokenCookieName,
                        acceptedAudience = config.openAm.acceptedAudience,
                        acceptedIssuer = config.openAm.acceptedIssuer
                    )
                )
            }
            config.azureAd?.let {
                log.info("Registering azure ad provider")
                register(
                    OAuthAuthProvider(
                        name = AzureAdAuthProvider,
                        appname = config.appName,
                        xForwardedPort = config.exposedPort,
                        config = it
                    )
                )
            }
        }
        config.azureAd?.let {
            installOAuthRoutes(
                OAuthFeature.Config(
                    appname = config.appName,
                    oidc = OidcClient(it.toOidcClientConfig()),
                    cookieEncryptionKey = it.cookieEncryptionKey,
                    exposedPort = config.exposedPort
                )
            )
        }
        installDefaultFeatures()
        install(DefaultHeaders) {
            applyCSPFeature(config.cspReportOnly, config.cspDirectives)
            applyReferrerPolicyFeature(config.referrerPolicy)
        }
        installNaisFeature(
            config.appName, config.appVersion, naisState,
            buildMap {
                put("ISSO_AUTH_PROVIDER", config.openAm != null)
                if (config.openAm != null) {
                    put("ISSO_CLIENT_ID", config.openAm.acceptedAudience)
                    put("ISSO_ISSUER", config.openAm.acceptedIssuer)
                    put("ISSO_WELL_KNOWN_URL", config.openAm.wellKnownUrl)
                }

                put("AZURE_AUTH_PROVIDER", config.azureAd != null)
                if (config.azureAd != null) {
                    put("AZURE_APP_CLIENT_ID", config.azureAd.clientId)
                    put("AZURE_APP_TENANT_ID", config.azureAd.tenantId)
                    put("AZURE_APP_WELL_KNOWN_URL", config.azureAd.wellKnownUrl)
                }
            }
        )
        installHostStaticFilesFeature(
            HostStaticFilesFeature.Config(
                appname = config.appName,
                rootFolder = staticFilesRootFolder
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
