package no.nav.modialogin.features.authfeature

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.Payload
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.util.*
import no.nav.modialogin.common.KtorServer

interface AuthProvider {
    val name: String
    suspend fun authorize(call: ApplicationCall): AuthFilterPrincipal?
    suspend fun onUnauthorized(call: ApplicationCall)
    enum class TokenType {
        ID_TOKEN, ACCESS_TOKEN
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

class AuthFilterPrincipal(val name: String, val token: String) : Principal, Payload by JWT.decode(token)
class AuthFilterPrincipals(val principals: Array<AuthFilterPrincipal>) : Principal

private val AuthenticationContextKey = AttributeKey<AuthenticationContext>("AuthContext")
private fun ApplicationCall.getAuthenticationContext(): AuthenticationContext {
    return this.attributes.computeIfAbsent(AuthenticationContextKey) {
        AuthenticationContext(this)
    }
}

const val AzureAdAuthProvider = "AzureAd"
const val OpenAmAuthProvider = "OpenAm"

val AuthFilterFeature = createApplicationPlugin("AuthFilterFeature", ::AuthFilterConfig) {
    if (this.application.pluginOrNull(Authentication) == null) {
        this.application.install(Authentication) {
            provider {
                // Just here to bypass config checker in ktors authentication feature
                skipWhen { true }
                authenticate {  }
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
            KtorServer.log.info("Authorizing for ${provider.name}")
            val principal: AuthFilterPrincipal? = provider.authorize(call)

            if (principal == null) {
                provider.onUnauthorized(call)
                return@onCall
            }
            principals[i] = principal
        }

        call
            .getAuthenticationContext()
            .principal(AuthFilterPrincipals(principals.requireNoNulls()))
    }
}
