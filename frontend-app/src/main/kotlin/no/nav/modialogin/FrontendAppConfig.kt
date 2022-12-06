package no.nav.modialogin

import io.getunleash.DefaultUnleash
import io.getunleash.Unleash
import io.getunleash.util.UnleashConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import no.nav.common.token_client.utils.env.AzureAdEnvironmentVariables
import no.nav.modialogin.Logging.log
import no.nav.modialogin.features.authfeature.OidcClient
import no.nav.modialogin.utils.AuthJedisPool
import no.nav.personoversikt.common.utils.ConditionalUtils
import no.nav.personoversikt.common.utils.EnvUtils.getConfig
import no.nav.personoversikt.common.utils.EnvUtils.getRequiredConfig
import java.io.File

class FrontendAppConfig {
    val appName: String = getRequiredConfig("APP_NAME")
    val appMode: AppMode = AppMode(getConfig("APP_MODE"))
    val appVersion: String = getRequiredConfig("APP_VERSION")
    val cspReportOnly: Boolean = getConfig("CSP_REPORT_ONLY")?.toBooleanStrictOrNull() ?: false
    val cspDirectives: String = getConfig("CSP_DIRECTIVES") ?: "default src 'self'"
    val referrerPolicy: String = getConfig("REFERRER_POLICY") ?: "origin"
    val proxyConfigFile: String = getConfig("PROXY_CONFIG_FILE") ?: "/proxy-config.json"
    val azureAd: AzureAdConfig = AzureAdConfig.load()
    val unleash: Unleash? = getConfig("UNLEASH_API_URL")?.let {
        val config = UnleashConfig
            .builder()
            .appName(getRequiredConfig("APP_NAME"))
            .unleashAPI(it)
            .build()
        DefaultUnleash(config)
    }
    val redis: AuthJedisPool? = ConditionalUtils.ifNotNull(
        getConfig("REDIS_HOST"),
        getConfig("REDIS_PASSWORD"),
    ) { host, password -> AuthJedisPool(host, password) }

    val database: DatabaseConfig? = ConditionalUtils.ifNotNull(
        getConfig("DATABASE_JDBC_URL"),
        getConfig("VAULT_MOUNTPATH")
    ) { url, mountPath -> DatabaseConfig(url, mountPath) }

    val cdnBucketUrl: String? = getConfig("CDN_BUCKET_URL")
    val proxyConfig: List<ProxyConfig> = readProxyConfig()

    init {
        check(redis != null || database != null) {
            "Could not find configuration for redis or PostgreSQL."
        }
    }

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

@Serializable
data class ProxyConfig(
    val prefix: String,
    val url: String? = null,
    val rewriteDirectives: List<String> = emptyList()
)

enum class AppMode(val locally: Boolean) {
    LOCALLY_WITHIN_DOCKER(true),
    LOCALLY_WITHIN_IDEA(true),
    NAIS(false);

    fun appport(): Int = when (this) {
        LOCALLY_WITHIN_DOCKER -> 8080
        LOCALLY_WITHIN_IDEA -> 8083
        NAIS -> 8080
    }

    fun hostport(): Int = when (this) {
        LOCALLY_WITHIN_DOCKER -> 8083
        LOCALLY_WITHIN_IDEA -> 8083
        NAIS -> 8080
    }

    companion object {
        operator fun invoke(appMode: String?): AppMode {
            return when (appMode) {
                null -> NAIS
                else -> AppMode.valueOf(appMode)
            }
        }
    }
}

class AzureAdConfig(
    val clientId: String,
    val clientSecret: String,
    val tenantId: String,
    val appJWK: String,
    preAuthorizedApps: String,
    val wellKnownUrl: String,
    val openidConfigIssuer: String,
    val openidConfigJWKSUri: String,
    val openidConfigTokenEndpoint: String,
) {
    val preAuthorizedApps = Json.decodeFromString<List<PreauthorizedApp>>(preAuthorizedApps)

    @Serializable
    class PreauthorizedApp(
        val name: String,
        val clientId: String
    )

    fun toOidcClientConfig() = OidcClient.Config(
        clientId = clientId,
        clientSecret = clientSecret,
        wellKnownUrl = wellKnownUrl
    )

    companion object {
        fun load(): AzureAdConfig {
            return kotlin.runCatching {
                val clientId = getRequiredConfig(AzureAdEnvironmentVariables.AZURE_APP_CLIENT_ID)
                val clientSecret = getRequiredConfig(AzureAdEnvironmentVariables.AZURE_APP_CLIENT_SECRET)
                val tenantId = getRequiredConfig(AzureAdEnvironmentVariables.AZURE_APP_TENANT_ID)
                val appJWK = getRequiredConfig(AzureAdEnvironmentVariables.AZURE_APP_JWK)
                val preAuthorizedApps = getRequiredConfig(AzureAdEnvironmentVariables.AZURE_APP_PRE_AUTHORIZED_APPS)
                val wellKnownUrl = getRequiredConfig(AzureAdEnvironmentVariables.AZURE_APP_WELL_KNOWN_URL)
                val openidConfigIssuer = getRequiredConfig(AzureAdEnvironmentVariables.AZURE_OPENID_CONFIG_ISSUER)
                val openidConfigJWKSUri = getRequiredConfig(AzureAdEnvironmentVariables.AZURE_OPENID_CONFIG_JWKS_URI)
                val openidConfigTokenEndpoint =
                    getRequiredConfig(AzureAdEnvironmentVariables.AZURE_OPENID_CONFIG_TOKEN_ENDPOINT)

                AzureAdConfig(
                    clientId = clientId,
                    clientSecret = clientSecret,
                    tenantId = tenantId,
                    appJWK = appJWK,
                    preAuthorizedApps = preAuthorizedApps,
                    wellKnownUrl = wellKnownUrl,
                    openidConfigIssuer = openidConfigIssuer,
                    openidConfigJWKSUri = openidConfigJWKSUri,
                    openidConfigTokenEndpoint = openidConfigTokenEndpoint,
                )
            }.getOrThrow()
        }
    }
}

data class DatabaseConfig(
    val jdbcUrl: String,
    val vaultMountpath: String,
)