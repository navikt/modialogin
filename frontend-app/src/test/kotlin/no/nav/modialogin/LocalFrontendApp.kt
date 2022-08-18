package no.nav.modialogin

import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.common.token_client.utils.env.AzureAdEnvironmentVariables
import no.nav.modialogin.auth.AzureAdConfig
import java.util.*

fun main() {
    System.setProperty("IS_LOCALHOST_DEV", "true")

    System.setProperty("APP_NAME", "frontend")
    System.setProperty("APP_VERSION", "localhost")
    System.setProperty("IDP_DISCOVERY_URL", "http://localhost:8080/openam/.well-known/openid-configuration")
    System.setProperty("IDP_CLIENT_ID", "foo")
    System.setProperty("IDP_ISSUER", "openam")
    System.setProperty("DELEGATED_LOGIN_URL", "http://localhost:8082/modialogin/api/start")
    System.setProperty("DELEGATED_REFRESH_URL", "http://localhost:8082/modialogin/api/refresh")
    System.setProperty("AUTH_TOKEN_RESOLVER", "modia_ID_token")
    System.setProperty("CSP_REPORT_ONLY", "true")
    System.setProperty("CSP_DIRECTIVES", "default-src 'self'; script-src 'self';")
    System.setProperty("REFERRER_POLICY", "no-referrer")
    System.setProperty("EXPOSED_PORT", "8083")
    System.setProperty("OUTSIDE_DOCKER", "true")
    System.setProperty("PROXY_CONFIG_FILE", "./frontend-app/proxy-config/proxy-config.json")
    System.setProperty("SECRET", "some secret")

    setupAzureAdEnv()

    startApplication()
}

fun setupAzureAdEnv() {
    val jwk = RSAKeyGenerator(2048)
        .keyUse(KeyUse.SIGNATURE) // indicate the intended use of the key
        .keyID(UUID.randomUUID().toString()) // give the key a unique ID
        .generate()
        .toJSONString()
    val preauthApps = Json.Default.encodeToString(
        listOf(
            AzureAdConfig.PreauthorizedApp(name = "other-app", clientId = "some-random-id"),
            AzureAdConfig.PreauthorizedApp(name = "another-app", clientId = "another-random-id"),
        )
    )

    System.setProperty(AzureAdEnvironmentVariables.AZURE_APP_TENANT_ID, "tenant")
    System.setProperty(AzureAdEnvironmentVariables.AZURE_APP_CLIENT_ID, "foo")
    System.setProperty(AzureAdEnvironmentVariables.AZURE_APP_CLIENT_SECRET, "app-client-secret")
    System.setProperty(AzureAdEnvironmentVariables.AZURE_APP_JWK, jwk)
    System.setProperty(AzureAdEnvironmentVariables.AZURE_APP_PRE_AUTHORIZED_APPS, preauthApps)
    System.setProperty(AzureAdEnvironmentVariables.AZURE_APP_WELL_KNOWN_URL, "http://localhost:8080/azuread/.well-known/openid-configuration")
    System.setProperty(AzureAdEnvironmentVariables.AZURE_OPENID_CONFIG_ISSUER, "azuread")
    System.setProperty(AzureAdEnvironmentVariables.AZURE_OPENID_CONFIG_JWKS_URI, "http://localhost:8080/azuread/.well-known/jwks.json")
    System.setProperty(AzureAdEnvironmentVariables.AZURE_OPENID_CONFIG_TOKEN_ENDPOINT, "http://localhost:8080/azuread/oauth/token")
}
