package no.nav.modialogin

import io.getunleash.DefaultUnleash
import io.getunleash.Unleash
import io.getunleash.util.UnleashConfig
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import no.nav.modialogin.auth.AzureAdConfig
import no.nav.modialogin.auth.OpenAmConfig
import no.nav.modialogin.common.KotlinUtils.getProperty
import no.nav.modialogin.common.KotlinUtils.requireProperty
import no.nav.modialogin.common.KtorServer.log
import no.nav.modialogin.features.bffproxyfeature.BFFProxyFeature.ProxyConfig
import java.io.File

class FrontendAppConfig {
    val appName: String = requireProperty("APP_NAME")
    val appVersion: String = requireProperty("APP_VERSION")
    val cspReportOnly: Boolean = getProperty("CSP_REPORT_ONLY")?.toBooleanStrictOrNull() ?: false
    val cspDirectives: String = getProperty("CSP_DIRECTIVES") ?: "default src 'self'"
    val referrerPolicy: String = getProperty("REFERRER_POLICY") ?: "origin"
    val exposedPort: Int = getProperty("EXPOSED_PORT")?.toIntOrNull() ?: 8080
    val proxyConfigFile: String = getProperty("PROXY_CONFIG_FILE") ?: "/proxy-config.json"
    val openAm: OpenAmConfig? = OpenAmConfig.load()
    val azureAd: AzureAdConfig? = AzureAdConfig.load()
    val unleash: Unleash? = getProperty("UNLEASH_API_URL")?.let {
        val config = UnleashConfig
            .builder()
            .appName(requireProperty("APP_NAME"))
            .unleashAPI(it)
            .build()
        DefaultUnleash(config)
    }
    val proxyConfig: List<ProxyConfig> = readProxyConfig()

    private fun readProxyConfig(): List<ProxyConfig> {
        val file = File(proxyConfigFile)
        return if (file.exists()) {
            log.info("Loading proxy-config from $proxyConfigFile")
            val content = file.readText()
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