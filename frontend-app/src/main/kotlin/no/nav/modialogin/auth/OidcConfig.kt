package no.nav.modialogin.auth

import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.JWKSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import no.nav.common.token_client.utils.env.AzureAdEnvironmentVariables
import no.nav.modialogin.common.KotlinUtils
import no.nav.modialogin.common.KtorServer.log

class OidcConfig(
    val clientId: String,
    val clientSecret: String,
    appJWK: String,
    preAuthorizedApps: String,
    val wellKnownUrl: String,
    val openidConfigIssuer: String,
    val openidConfigJWKSUri: String,
    val openidConfigTokenEndpoint: String,
) {
    val appJWK: JWK = JWK.parse(appJWK)
    val preAuthorizedApps = Json.Default.decodeFromString<List<PreauthorizedApp>>(preAuthorizedApps)

    @Serializable
    class PreauthorizedApp(
        val name: String,
        val clientId: String
    )

    companion object {
        fun load(): OidcConfig? {
            return kotlin.runCatching {
                val clientId = KotlinUtils.requireProperty(AzureAdEnvironmentVariables.AZURE_APP_CLIENT_ID)
                val clientSecret = KotlinUtils.requireProperty(AzureAdEnvironmentVariables.AZURE_APP_CLIENT_SECRET)
                val appJWK = KotlinUtils.requireProperty(AzureAdEnvironmentVariables.AZURE_APP_JWK)
                val preAuthorizedApps =
                    KotlinUtils.requireProperty(AzureAdEnvironmentVariables.AZURE_APP_PRE_AUTHORIZED_APPS)
                val wellKnownUrl = KotlinUtils.requireProperty(AzureAdEnvironmentVariables.AZURE_APP_WELL_KNOWN_URL)
                val openidConfigIssuer =
                    KotlinUtils.requireProperty(AzureAdEnvironmentVariables.AZURE_OPENID_CONFIG_ISSUER)
                val openidConfigJWKSUri =
                    KotlinUtils.requireProperty(AzureAdEnvironmentVariables.AZURE_OPENID_CONFIG_JWKS_URI)
                val openidConfigTokenEndpoint =
                    KotlinUtils.requireProperty(AzureAdEnvironmentVariables.AZURE_OPENID_CONFIG_TOKEN_ENDPOINT)

                OidcConfig(
                    clientId = clientId,
                    clientSecret = clientSecret,
                    appJWK = appJWK,
                    preAuthorizedApps = preAuthorizedApps,
                    wellKnownUrl = wellKnownUrl,
                    openidConfigIssuer = openidConfigIssuer,
                    openidConfigJWKSUri = openidConfigJWKSUri,
                    openidConfigTokenEndpoint = openidConfigTokenEndpoint,
                )
            }.getOrElse {
                log.info("Could not load azureAd config", it)
                null
            }
        }
    }
}
