package no.nav.modialogin

import no.nav.modialogin.common.KotlinUtils.getProperty
import no.nav.modialogin.common.KotlinUtils.requireProperty

class LoginAppConfig {
    val appName: String = requireProperty("APP_NAME")
    val appVersion: String = requireProperty("APP_VERSION")
    val idpDiscoveryUrl: String = requireProperty("IDP_DISCOVERY_URL")
    val idpClientId: String = requireProperty("IDP_CLIENT_ID")
    val idpClientSecret: String = requireProperty("IDP_CLIENT_SECRET")
    val authTokenResolver: String = getProperty("AUTH_TOKEN_RESOLVER") ?: "ID_token"
    val refreshTokenResolver: String = getProperty("REFRESH_TOKEN_RESOLVER") ?: "refresh_token"
    val exposedPort: Int = getProperty("EXPOSED_PORT")?.toIntOrNull() ?: 8080
}
