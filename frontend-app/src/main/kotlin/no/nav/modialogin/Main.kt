package no.nav.modialogin

import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import kotlinx.serialization.builtins.serializer
import no.nav.modialogin.Logging.log
import no.nav.modialogin.features.*
import no.nav.modialogin.features.authfeature.*
import no.nav.modialogin.features.bffproxyfeature.BFFProxyFeature
import no.nav.modialogin.features.csp.CSPFeature
import no.nav.modialogin.persistence.Persistence
import no.nav.modialogin.persistence.RedisPersistence
import no.nav.modialogin.utils.AuthJedisPool
import no.nav.personoversikt.common.ktor.utils.KtorServer
import no.nav.personoversikt.common.ktor.utils.Metrics
import no.nav.personoversikt.common.ktor.utils.Selftest

fun main() {
    startApplication()
}
fun startApplication() {
    val config = FrontendAppConfig()
    val staticFilesRootFolder = if (config.appMode == AppMode.LOCALLY_WITHIN_IDEA) "./frontend-app/www" else "/www"
    val port = config.appMode.appport()
    val redisPool = AuthJedisPool(config.redis)

    val sessionPersistence: Persistence<String, TokenPrincipal> = RedisPersistence(
        scope = "session",
        redisPool = redisPool,
        keySerializer = String.serializer(),
        valueSerializer = TokenPrincipal.serializer()
    )
    val bffProxyPersistence: Persistence<String, String> = RedisPersistence(
        scope = "bffproxy",
        redisPool = redisPool,
        keySerializer = String.serializer(),
        valueSerializer = String.serializer()
    )

    log.info("Starting app: $port")

    KtorServer.create(Netty, port) {
        install(OAuth2SessionAuthentication) {
            appname = config.appName
            appmode = config.appMode
            azureConfig = config.azureAd
            persistence = sessionPersistence
            skipWhen = { call ->
                val url = call.request.uri
                val isInternal = url.contains("/${config.appName}/internal/")
                isInternal
            }
        }

        install(Selftest.Plugin) {
            appname = config.appName
            version = config.appVersion
            contextpath = config.appName
        }

        install(Metrics.Plugin) {
            contextpath = config.appName
        }

        install(CSPFeature) {
            reportOnly = config.cspReportOnly
            directive = config.cspDirectives
        }

        install(DefaultFeatures) {
            referrerPolicy(config.referrerPolicy)
        }


        if (config.cdnBucketUrl != null) {
            install(CDNHosting) {
                contextpath = config.appName
                cdnUrl = config.cdnBucketUrl
                unleash = config.unleash
            }
        } else {
            install(StaticFileHosting) {
                contextpath = config.appName
                rootFolder = staticFilesRootFolder
                unleash = config.unleash
            }
        }


        install(BFFProxyFeature) {
            appName = config.appName
            proxyConfig = config.proxyConfig
            azureAdConfig = config.azureAd
            persistence = bffProxyPersistence
        }
    }.start(wait = true)
}
