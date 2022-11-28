package no.nav.modialogin

import io.getunleash.DefaultUnleash
import io.getunleash.Unleash
import io.getunleash.util.UnleashConfig
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import no.nav.modialogin.auth.AzureAdConfig
import no.nav.modialogin.common.KotlinUtils.getProperty
import no.nav.modialogin.common.KotlinUtils.requireProperty
import no.nav.modialogin.common.KtorServer.log
import no.nav.modialogin.features.bffproxyfeature.BFFProxyFeature.ProxyConfig
import java.io.File

class FrontendAppConfig {
    val appMode: ApplicationMode = ApplicationMode.parse(getProperty("APP_MODE"))
    val appName: String = requireProperty("APP_NAME")
    val appVersion: String = requireProperty("APP_VERSION")
    val cspReportOnly: Boolean = getProperty("CSP_REPORT_ONLY")?.toBooleanStrictOrNull() ?: false
    val cspDirectives: String = getProperty("CSP_DIRECTIVES") ?: "default src 'self'"
    val referrerPolicy: String = getProperty("REFERRER_POLICY") ?: "origin"
    val proxyConfigFile: String = getProperty("PROXY_CONFIG_FILE") ?: "/proxy-config.json"
    val azureAd: AzureAdConfig = AzureAdConfig.load()
    val redis: RedisConfig = RedisConfig(
        host = requireProperty("REDIS_HOST"),
        password = requireProperty("REDIS_PASSWORD"),
    )
    val unleash: Unleash? = getProperty("UNLEASH_API_URL")?.let {
        val config = UnleashConfig
            .builder()
            .appName(requireProperty("APP_NAME"))
            .unleashAPI(it)
            .build()
        DefaultUnleash(config)
    }
    val cdnBucketUrl: String? = getProperty("CDN_BUCKET_URL")
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
class RedisConfig(
    val host: String,
    val password: String,
)
