package no.nav.modialogin.features.authfeature

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import no.nav.modialogin.ApplicationMode
import no.nav.modialogin.RedisConfig
import no.nav.modialogin.auth.OidcClient
import no.nav.modialogin.common.*
import no.nav.modialogin.common.features.WhoAmIPrincipal
import java.net.URL
import java.security.interfaces.RSAPublicKey
import kotlin.collections.listOf

class SessionAuthConfig(
    var appmode: ApplicationMode? = null,
    var appname: String? = null,
    var oidcConfig: OidcClient.Config? = null,
    var redisConfig: RedisConfig? = null,
    var skipWhen: (ApplicationCall) -> Boolean = { false },
    var useMock: Boolean = false,
)

class JWTSerializer : KSerializer<DecodedJWT> {
    override val descriptor: SerialDescriptor = String.serializer().descriptor

    override fun deserialize(decoder: Decoder): DecodedJWT {
        return JWT.decode(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: DecodedJWT) {
        encoder.encodeString(value.token)
    }
}

@Serializable
class SessionAuthPrincipal(
    @Serializable(with = JWTSerializer::class)
    val accessToken: DecodedJWT,
    val refreshToken: String? = null,
): Principal, WhoAmIPrincipal {
    override val description: String = accessToken.getClaim("NAVident").asString() ?: accessToken.subject
}

typealias SessionId = String

private const val oauthAuthProvider = "oauth"
val SessionOauthFeature = createApplicationPlugin("SessionAuth", ::SessionAuthConfig) {
    val appmode: ApplicationMode = requireNotNull(pluginConfig.appmode) {
        "appmode must be defined"
    }
    val appname: String = requireNotNull(pluginConfig.appname) {
        "appname must be defined"
    }
    val oidcConfig: OidcClient.Config = requireNotNull(pluginConfig.oidcConfig) {
        "oidcConfig must be defined"
    }
    val redisConfig: RedisConfig = requireNotNull(pluginConfig.redisConfig) {
        "redisHost must be defined"
    }

    val skipWhenPredicate: (ApplicationCall) -> Boolean = pluginConfig.skipWhen
    val useMock: Boolean = pluginConfig.useMock
    val sessionCookieName = "$appname-session"
    val oidcClient = OidcClient(oidcConfig)
    val oidcWellknown = oidcClient.wellKnown
    val oidcJWK : JwkProvider by lazy {
        JwkProviderBuilder(URL(oidcWellknown.jwksUrl))
            .cached(true)
            .rateLimited(true)
            .build()
    }
    val cache = RedisCaffeineSessionCache(redisConfig, oidcClient)

    with(this.application) {
        install(Sessions) {
            cookie<SessionId>(sessionCookieName) {
                cookie.path = "/$appname"
                cookie.httpOnly = true
                cookie.encoding = CookieEncoding.RAW
                serializer = object : SessionSerializer<SessionId> {
                    override fun deserialize(text: String): SessionId = text
                    override fun serialize(session: SessionId): String = session
                }
            }
        }

        install(Authentication) {
            if (useMock) {
                provider {
                    authenticate {
                        val token = JWT.create().withClaim("NAVident", "Z999999").sign(Algorithm.none())
                        it.principal(SessionAuthPrincipal(JWT.decode(token), "refreshtoken"))
                    }
                }
            } else {
                oauth(oauthAuthProvider) {
                    skipWhen(skipWhenPredicate)
                    urlProvider = {
                        // Call should be /oauth2/login here
                        // Get 'redirect' queryparam, and pass-on to callback-url
                        val current = request.origin.scheme
                        val returnUrl: String = requireNotNull(request.queryParameters["redirect"]) {
                            "redirect url null when providing callback url"
                        }
                        redirectUrl("/$appname/oauth2/callback?redirect=${KtorUtils.encode(returnUrl)}", appmode.locally)
                    }
                    providerLookup = {
                        OAuthServerSettings.OAuth2ServerSettings(
                            name = "AzureAD",
                            authorizeUrl = oidcWellknown.authorizationEndpoint,
                            accessTokenUrl = oidcWellknown.tokenEndpoint,
                            requestMethod = HttpMethod.Post,
                            clientId = oidcConfig.clientId,
                            clientSecret = oidcConfig.clientSecret,
                            defaultScopes = listOf("openid", "offline_access", "api://${oidcConfig.clientId}/.default")
                        )
                    }
                    client = HttpClient(Apache) {
                        useProxy()
                        json()
                        logging()
                    }
                }

                session<SessionId> {
                    skipWhen(skipWhenPredicate)
                    validate {sessionId: SessionId ->
                        val principal = cache.get(sessionId) ?: return@validate null
                        val token = principal.accessToken
                        try {
                            val jwk = oidcJWK.get(token.keyId)
                            val algorithm = Algorithm.RSA256(jwk.publicKey as RSAPublicKey, null)
                            JWT
                                .require(algorithm)
                                .withAnyOfAudience(oidcConfig.clientId)
                                .withIssuer(oidcWellknown.issuer)
                                .build()
                                .verify(token)

                            principal
                        } catch (e: Throwable) {
                            KtorServer.log.info("Could not verify token", e)
                            null
                        }
                    }

                    challenge {
                        // call should be any url supported by the application
                        // get the url and pass on to /login?redirect=[url]
                        val port = if (appmode.hostport() == 8080) "" else ":${appmode.hostport()}"
                        val originalUri = "${call.request.origin.scheme}://${call.request.host()}$port${call.request.uri}"

                        call.respondRedirect("/$appname/oauth2/login?redirect=${KtorUtils.encode(originalUri)}")
                    }
                }
            }
        }

        routing {
            route(appname) {
                route("oauth2") {
                    authenticate(oauthAuthProvider) {
                        get("login") {
                            // Needed for oauth authprovider to work
                            // Will automagically redirect to 'authorizeUrl'
                        }
                        get("callback") {
                            val oauthPrincipal: OAuthAccessTokenResponse.OAuth2? = call.principal()
                            if (oauthPrincipal == null) {
                                call.respondText("Could not get principal from OAuth callback", status = HttpStatusCode.Unauthorized)
                            } else {
                                val sessionId = GenerateOnlyNonceManager.newNonce()
                                val sessionPrincipal = SessionAuthPrincipal(
                                    accessToken = JWT.decode(oauthPrincipal.accessToken),
                                    refreshToken = oauthPrincipal.refreshToken,
                                )

                                cache.put(sessionId, sessionPrincipal)
                                call.sessions.set(sessionId)

                                val originalUrl = call.request.queryParameters["redirect"]
                                    ?.let(KtorUtils::decode)
                                    ?: "/$appname"

                                call.respondRedirect(
                                    permanent = false,
                                    url = originalUrl
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ApplicationCall.redirectUrl(path: String, development: Boolean): String {
    val protocol = if (!development) "https" else request.origin.scheme
    val defaultPort = if (protocol == "http") 80 else 443
    val host = if(!development) request.host() else "localhost"

    val hostPort = host + request.port().let { port ->
        if (port == defaultPort || !development) "" else ":$port"
    }
    return "$protocol://$hostPort$path"
}
