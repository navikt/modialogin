package no.nav.modialogin.features.authfeature

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import no.nav.modialogin.AppMode
import no.nav.modialogin.AzureAdConfig
import no.nav.modialogin.persistence.Persistence
import java.net.URL
import java.security.interfaces.RSAPublicKey
import java.util.UUID

typealias SessionId = String

@Serializable
class TokenPrincipal(
    @Serializable(with = Serializer::class)
    val accessToken: DecodedJWT,
    val refreshToken: String? = null
) : Principal {
    class Serializer : KSerializer<DecodedJWT> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
            "DecodedJWT",
            PrimitiveKind.STRING
        )

        override fun deserialize(decoder: Decoder): DecodedJWT {
            return JWT.decode(decoder.decodeString())
        }

        override fun serialize(encoder: Encoder, value: DecodedJWT) {
            encoder.encodeString(value.token)
        }
    }
}

class Oauth2SessionAuthenticationConfig(
    var appname: String? = null,
    var appmode: AppMode? = null,
    var azureConfig: AzureAdConfig? = null,
    var persistence: Persistence<String, TokenPrincipal>? = null,
    var skipWhen: ((ApplicationCall) -> Boolean)? = null,
)

val OAuth2SessionAuthentication = createApplicationPlugin("security", ::Oauth2SessionAuthenticationConfig) {
    val appname = checkNotNull(pluginConfig.appname) { "appname is required" }
    val appmode = checkNotNull(pluginConfig.appmode) { "appmode is required" }
    val sessionCookieName = "${appname}_sessionid"
    val callbackCookieName = "${appname}_callback"
    val azureConfig = checkNotNull(pluginConfig.azureConfig) { "azureConfig is required" }
    val persistence = requireNotNull(pluginConfig.persistence) { "persistence is required" }
    val skipWhenPredicate = pluginConfig.skipWhen
    val oidcClient = OidcClient(azureConfig.toOidcClientConfig())
    val oidcWellknown : OidcClient.WellKnownResult by lazy { oidcClient.wellKnown }
    val cache = SessionCache(oidcClient, persistence)
    val jwkProvider = JwkProviderBuilder(URL(azureConfig.openidConfigJWKSUri))
        .cached(true)
        .rateLimited(true)
        .build()

    with(application) {
        install(Sessions) {
            cookie<SessionId>(sessionCookieName) {
                cookie.path = "/$appname"
                cookie.httpOnly = true
                cookie.secure = !appmode.locally
                cookie.encoding = CookieEncoding.RAW
                serializer = object : SessionSerializer<SessionId> {
                    override fun deserialize(text: String): SessionId = text
                    override fun serialize(session: SessionId): String = session
                }
            }
        }

        install(Authentication) {
            session<SessionId> {
                if (skipWhenPredicate != null) {
                    skipWhen(skipWhenPredicate)
                }

                validate { sessionId ->
                    cache.get(sessionId)
                        ?.also { verifyAccessToken(jwkProvider, it.accessToken, azureConfig, oidcWellknown) }
                        ?: return@validate null
                }

                challenge {
                    // call should be any url supported by the application
                    // get the url and pass on to /login?redirect=[url]
                    val port = if (appmode.hostport() == 8080) "" else ":${appmode.hostport()}"
                    val originalUri = "${call.request.origin.scheme}://${call.request.host()}$port${call.request.uri}"
                    call.response.cookies.append(
                        Cookie(
                            name = callbackCookieName,
                            value = originalUri,
                            path = "/$appname",
                            maxAge = 3600,
                            secure = !appmode.locally,
                            httpOnly = true
                        )
                    )
                    call.respondRedirect("/$appname/oauth2/login")
                }
            }

            oauth("oauth") {
                urlProvider = {
                    redirectUrl("/$appname/oauth2/callback", appmode.locally)
                }
                if (skipWhenPredicate != null) {
                    skipWhen(skipWhenPredicate)
                }

                providerLookup = {
                    OAuthServerSettings.OAuth2ServerSettings(
                        name = "AzureAD",
                        authorizeUrl = oidcWellknown.authorizationEndpoint,
                        accessTokenUrl = oidcWellknown.tokenEndpoint,
                        requestMethod = HttpMethod.Post,
                        clientId = azureConfig.clientId,
                        clientSecret = azureConfig.clientSecret,
                        defaultScopes = listOf("openid", "offline_access", "api://${azureConfig.clientId}/.default"),
                    )
                }
                client = oidcClient.httpClient
            }
        }

        routing {
            route(appname) {
                route("oauth2") {
                    authenticate("oauth") {
                        get("/login") {
                            // Redirects to 'authorizeUrl' automatically
                        }

                        get("/callback") {
                            val oauthPrincipal: OAuthAccessTokenResponse.OAuth2? = call.principal()
                            if (oauthPrincipal == null) {
                                call.respond(status = HttpStatusCode.BadRequest, "No OAuth principal found")
                            } else {
                                val sessionId = UUID.randomUUID().toString()
                                val accessToken = JWT.decode(oauthPrincipal.accessToken)
                                verifyAccessToken(jwkProvider, accessToken, azureConfig, oidcWellknown)

                                val tokenPrincipal = TokenPrincipal(
                                    accessToken = accessToken,
                                    refreshToken = oauthPrincipal.refreshToken
                                )

                                cache.put(sessionId, tokenPrincipal)
                                call.sessions.set(sessionCookieName, sessionId)

                                val originalUrl = call.request.cookies[callbackCookieName] ?: "/$appname"
                                call.response.cookies.appendExpired(
                                    name = callbackCookieName,
                                    path = "/$appname",
                                )
                                call.respondRedirect(url = originalUrl, permanent = false)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun verifyAccessToken(
    jwkProvider: JwkProvider,
    accessToken: DecodedJWT,
    azureConfig: AzureAdConfig,
    oidcWellknown: OidcClient.WellKnownResult
) {
    val key = jwkProvider.get(accessToken.keyId)
    val algorithm = Algorithm.RSA256(key.publicKey as RSAPublicKey, null)

    JWT
        .require(algorithm)
        .withAudience(azureConfig.clientId)
        .withIssuer(oidcWellknown.issuer)
        .build()
        .verify(accessToken)
}

private fun ApplicationCall.redirectUrl(path: String, development: Boolean): String {
    val protocol = if (!development) "https" else request.origin.scheme
    val defaultPort = if (protocol == "http") 80 else 443
    val host = if (!development) request.host() else "localhost"

    val hostPort = host + request.port().let { port ->
        if (port == defaultPort || !development) "" else ":$port"
    }
    return "$protocol://$hostPort$path"
}
