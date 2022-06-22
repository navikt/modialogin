package no.nav.modialogin.features.authfeature

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.Payload
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.util.date.*
import no.nav.modialogin.common.KtorServer.log
import no.nav.modialogin.common.features.WhoAmIPrincipal
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

interface AuthProvider {
    val name: String
    suspend fun authorize(call: ApplicationCall): AuthFilterPrincipal?
    suspend fun onUnauthorized(call: ApplicationCall)
}

abstract class BaseAuthProvider : AuthProvider {
    abstract suspend fun getToken(call: ApplicationCall): String?
    abstract fun verify(jwt: DecodedJWT)
    abstract suspend fun getRefreshToken(call: ApplicationCall): String?
    abstract suspend fun refreshTokens(call: ApplicationCall, refreshToken: String): String
    override suspend fun authorize(call: ApplicationCall): AuthFilterPrincipal? {
        var token = getToken(call) ?: return null
        val jwt = JWT.decode(token)
        try {
            verify(jwt)
        } catch (e: Throwable) {
            log.warn("JWT-verification failed", e)
            return null
        }

        val refreshToken = getRefreshToken(call)
        if (refreshToken != null && jwt.expiresWithin(5.minutes)) {
            token = refreshTokens(call, refreshToken)
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
