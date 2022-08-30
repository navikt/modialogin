package no.nav.modialogin.auth

import no.nav.modialogin.common.KotlinUtils.getProperty
import no.nav.modialogin.common.KotlinUtils.requireProperty

class OpenAmConfig(
    val wellKnownUrl: String,
    val acceptedAudience: String,
    val acceptedIssuer: String,
    val loginUrl: String,
    val refreshUrl: String,
    val idTokenCookieName: String = "ID_token",
    val refreshTokenCookieName: String? = "refresh_token",
) {
    companion object {
        fun load(): OpenAmConfig? {
            val disableOpenAm = getProperty("DISABLE_OPEN_AM")?.toBooleanStrictOrNull() ?: false
            if (disableOpenAm) {
                return null
            }

            return runCatching {
                val wellKnownUrl = requireProperty("IDP_DISCOVERY_URL")
                val acceptedAudience = requireProperty("IDP_CLIENT_ID")
                val acceptedIssuer = requireProperty("IDP_ISSUER")
                val loginUrl = requireProperty("DELEGATED_LOGIN_URL")
                val refreshUrl = requireProperty("DELEGATED_REFRESH_URL")
                val idTokenCookieName = getProperty("AUTH_TOKEN_RESOLVER") ?: "ID_token"
                val refreshTokenCookieName = getProperty("REFRESH_TOKEN_RESOLVER") ?: "refresh_token"

                OpenAmConfig(
                    wellKnownUrl = wellKnownUrl,
                    acceptedAudience = acceptedAudience,
                    acceptedIssuer = acceptedIssuer,
                    loginUrl = loginUrl,
                    refreshUrl = refreshUrl,
                    idTokenCookieName = idTokenCookieName,
                    refreshTokenCookieName = refreshTokenCookieName,
                )
            }.getOrElse {
                null
            }
        }
    }
}