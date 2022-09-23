package no.nav.modialogin.features.authfeature

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.Payload
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.util.date.*
import kotlinx.coroutines.runBlocking
import no.nav.modialogin.auth.OidcClient
import no.nav.modialogin.common.*
import no.nav.modialogin.common.KtorServer.log
import no.nav.modialogin.common.features.WhoAmIPrincipal
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

interface AuthProvider {
    val name: String
    suspend fun authorize(call: ApplicationCall): AuthFilterPrincipal?
    suspend fun onUnauthorized(call: ApplicationCall)
}

abstract class BaseAuthProvider(private val wellKnownUrl: OidcClient.Url) : AuthProvider {
    private val httpClient = HttpClient(Apache) {
        if (wellKnownUrl is OidcClient.Url.External) {
            useProxy()
        }
        logging()
        json()
        defaultRequest {
            header(HttpHeaders.CacheControl, "no-cache")
            header(HttpHeaders.XCorrelationId, KotlinUtils.callId())
        }
    }
    private val wellKnown: OidcClient.WellKnownResult by lazy {
        runBlocking {
            KotlinUtils.retry(10, 2.seconds) {
                log.info("Fetching oidc from $wellKnownUrl")
                httpClient.get(URL(wellKnownUrl.url)).body()
            }
        }
    }

    protected val jwkProvider: JwkProvider by lazy {
        JwkProviderBuilder(URL(wellKnown.jwksUrl))
            .cached(10, 24, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()
    }

    abstract suspend fun getToken(call: ApplicationCall): String?
    abstract suspend fun refreshTokens(call: ApplicationCall, refreshToken: String): String
    abstract fun verify(token: String): DecodedJWT
    abstract suspend fun getRefreshToken(call: ApplicationCall): String?
    override suspend fun authorize(call: ApplicationCall): AuthFilterPrincipal? {
        var token = getToken(call) ?: return null
        val jwt = try {
            verify(token)
        } catch (e: Throwable) {
            log.warn("JWT-verification failed", e)
            return null
        }

        try {
            val refreshToken = getRefreshToken(call)
            if (refreshToken != null && jwt.expiresWithin(5.minutes)) {
                token = refreshTokens(call, refreshToken)
            }
        } catch (e: Throwable) {
            log.error("Could not refresh token", e)
        }

        return AuthFilterPrincipal(name, token)
    }

    private fun DecodedJWT.expiresWithin(time: Duration): Boolean {
        val expiry = this.expiresAt.time - time.inWholeMilliseconds
        return getTimeMillis() > expiry
    }
}

class AuthFilterConfig(
    providers: List<AuthProvider> = emptyList(),
    var ignorePattern: (ApplicationCall) -> Boolean = { false },
) {
    val providers = providers.toMutableList()
    fun register(provider: AuthProvider) {
        providers.add(provider)
    }
}

class AuthFilterPrincipal(val name: String, val token: String) : Payload by JWT.decode(token), WhoAmIPrincipal {
    override val description: String = subject
}
class AuthFilterPrincipals(val principals: Array<AuthFilterPrincipal>) : WhoAmIPrincipal {
    override val description: String = principals.joinToString(", ") { "${it.name}: ${it.description}" }
}

const val AzureAdAuthProvider = "AzureAd"
const val OpenAmAuthProvider = "OpenAm"

val AuthFilterFeature = createApplicationPlugin("AuthFilterFeature", ::AuthFilterConfig) {
    if (this.application.pluginOrNull(Authentication) == null) {
        this.application.install(Authentication) {
            provider {
                // Just here to bypass config checker in ktors authentication feature
                skipWhen { true }
                authenticate { }
            }
        }
    }
    onCall { call ->
        if (pluginConfig.ignorePattern(call)) {
            return@onCall
        }

        val providers = pluginConfig.providers
        val principals = arrayOfNulls<AuthFilterPrincipal>(providers.size)

        for (i in providers.indices) {
            val provider = providers[i]
            log.info("Authorizing for ${provider.name}")
            val principal: AuthFilterPrincipal? = provider.authorize(call)

            if (principal == null) {
                provider.onUnauthorized(call)
                return@onCall
            }
            principals[i] = principal
        }

        call.authentication.principal(AuthFilterPrincipals(principals.requireNoNulls()))
    }
}
