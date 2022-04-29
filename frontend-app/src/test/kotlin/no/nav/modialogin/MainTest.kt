package no.nav.modialogin

import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import no.nav.common.token_client.utils.env.AzureAdEnvironmentVariables
import java.util.*

fun main() {
    val rsaKey = RSAKeyGenerator(2048)
        .keyUse(KeyUse.SIGNATURE) // indicate the intended use of the key
        .keyID(UUID.randomUUID().toString()) // give the key a unique ID
        .generate()
        .toJSONString()

    System.setProperty("APP_NAME", "frontend")
    System.setProperty("APP_VERSION", "localhost")
    System.setProperty("IDP_DISCOVERY_URL", "http://localhost:8080/.well-known/openid-configuration")
    System.setProperty("IDP_CLIENT_ID", "foo")
    System.setProperty("DELEGATED_LOGIN_URL", "http://localhost:8082/modialogin/api/start")
    System.setProperty("AUTH_TOKEN_RESOLVER", "modia_ID_token")
    System.setProperty("CSP_REPORT_ONLY", "true")
    System.setProperty("CSP_DIRECTIVES", "default-src 'self'; script-src 'self';")
    System.setProperty("REFERRER_POLICY", "no-referrer")
    System.setProperty("EXPOSED_PORT", "8083")

    System.setProperty(AzureAdEnvironmentVariables.AZURE_APP_CLIENT_ID, "frontend-app-id")
    System.setProperty(AzureAdEnvironmentVariables.AZURE_APP_JWK, rsaKey)
    System.setProperty(AzureAdEnvironmentVariables.AZURE_OPENID_CONFIG_TOKEN_ENDPOINT, "http://localhost:8080/oboflow")

    startApplication()
}
