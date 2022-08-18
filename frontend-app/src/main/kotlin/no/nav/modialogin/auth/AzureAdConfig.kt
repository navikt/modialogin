package no.nav.modialogin.auth

import com.nimbusds.jose.jwk.JWK
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import no.nav.common.token_client.utils.env.AzureAdEnvironmentVariables
import no.nav.modialogin.common.KotlinUtils.getProperty
import no.nav.modialogin.common.KotlinUtils.requireProperty
import no.nav.modialogin.common.KtorServer.log

class AzureAdConfig(
    val clientId: String,
    val clientSecret: String,
    val tenantId: String,
    appJWK: String,
    preAuthorizedApps: String,
    val wellKnownUrl: String,
    val openidConfigIssuer: String,
    val openidConfigJWKSUri: String,
    val openidConfigTokenEndpoint: String,
    val cookieEncryptionKey: String?,
) {
    val appJWK: JWK = JWK.parse(appJWK)
    val preAuthorizedApps = Json.Default.decodeFromString<List<PreauthorizedApp>>(preAuthorizedApps)

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
        fun load(): AzureAdConfig? {
            val disableAzureAd = getProperty("DISABLE_AZURE_AD")?.toBooleanStrictOrNull() ?: false
            if (disableAzureAd) {
                return null
            }

            return kotlin.runCatching {
                val clientId = requireProperty(AzureAdEnvironmentVariables.AZURE_APP_CLIENT_ID)
                val clientSecret = requireProperty(AzureAdEnvironmentVariables.AZURE_APP_CLIENT_SECRET)
                val tenantId = requireProperty(AzureAdEnvironmentVariables.AZURE_APP_TENANT_ID)
                val appJWK = requireProperty(AzureAdEnvironmentVariables.AZURE_APP_JWK)
                val preAuthorizedApps = requireProperty(AzureAdEnvironmentVariables.AZURE_APP_PRE_AUTHORIZED_APPS)
                val wellKnownUrl = requireProperty(AzureAdEnvironmentVariables.AZURE_APP_WELL_KNOWN_URL)
                val openidConfigIssuer = requireProperty(AzureAdEnvironmentVariables.AZURE_OPENID_CONFIG_ISSUER)
                val openidConfigJWKSUri = requireProperty(AzureAdEnvironmentVariables.AZURE_OPENID_CONFIG_JWKS_URI)
                val openidConfigTokenEndpoint = requireProperty(AzureAdEnvironmentVariables.AZURE_OPENID_CONFIG_TOKEN_ENDPOINT)
                val encryptionSecret = getProperty("COOKIE_ENCRYPTION_KEY")

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
                    cookieEncryptionKey = encryptionSecret
                )
            }.getOrElse {
                log.info("Could not load azureAd config", it)
                null
            }
        }
    }
}
