package no.nav.modialogin.features.oauthfeature

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object OAuth {
    fun cookieName(appname: String): String = "${appname}_tokens"

    @Serializable
    class CookieTokens(
        @SerialName("id_token") val idToken: String,
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String,
    )
}
