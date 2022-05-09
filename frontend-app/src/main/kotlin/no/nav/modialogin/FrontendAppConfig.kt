package no.nav.modialogin

import dev.nohus.autokonfig.types.BooleanSetting
import dev.nohus.autokonfig.types.IntSetting
import dev.nohus.autokonfig.types.StringSetting
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import no.nav.modialogin.common.KtorServer.log
import no.nav.modialogin.common.features.bffproxyfeature.BFFProxyFeature.ProxyConfig
import java.io.File

class FrontendAppConfig(proxyConfigFile: File) {
    val appName by StringSetting()
    val appVersion by StringSetting()
    val idpDiscoveryUrl by StringSetting()
    val idpClientId by StringSetting()
    val delegatedLoginUrl by StringSetting()
    val authTokenResolver by StringSetting(default = "ID_token")
    val cspReportOnly by BooleanSetting(default = false)
    val cspDirectives by StringSetting(default = "default src 'self'")
    val referrerPolicy by StringSetting(default = "origin")
    val exposedPort by IntSetting(default = 8080)
    val proxyConfig: List<ProxyConfig> by lazy {
        if (proxyConfigFile.exists()) {
            val content = proxyConfigFile.readText()
            Json
                .runCatching {
                    decodeFromString<List<ProxyConfig>>(content)
                }
                .onFailure { log.warn("Could not decode proxy-config", it) }
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }
}