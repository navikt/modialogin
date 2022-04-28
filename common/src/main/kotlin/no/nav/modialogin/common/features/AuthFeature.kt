package no.nav.modialogin.common.features

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.interfaces.Payload
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.http.auth.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.concurrent.TimeUnit

class AuthFeature(private val config: Config) {
    val log: Logger = LoggerFactory.getLogger("AuthFeature")
    val TEN_MINUTES: Long = 10 * 60 * 1000
    companion object {
        fun Application.installAuthFeature(config: Config) {
            AuthFeature(config).install(this)
        }
    }
    data class Config(
        var jwksUrl: String = "",
        val authTokenResolver: String,
        var acceptedAudience: String = ""
    )
    class PayloadPrincipal(val payload: Payload) : Principal, Payload by payload

    fun install(application: Application) {
        with(application) {
            install(Authentication) {
                jwt {
                    authHeader { useJwtFromCookie(it) }
                    verifier(makeJwkProvider(config.jwksUrl))
                    validate { validateJWT(it, config.acceptedAudience) }
                }
            }
        }
    }

    fun useJwtFromCookie(call: ApplicationCall): HttpAuthHeader? {
        val idToken = config.authTokenResolver
        return try {
            val token = call.request.cookies[idToken]
            parseAuthorizationHeader("Bearer $token")
        } catch (ex: Throwable) {
            log.warn("Could not get JWT from cookie '$idToken'", ex)
            null
        }
    }

    private fun makeJwkProvider(jwksUrl: String): JwkProvider =
        JwkProviderBuilder(URL(jwksUrl))
            .cached(10, 24, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()

    private fun validateJWT(credentials: JWTCredential, requiredAudience: String): Principal {
        val tokenAudience = credentials.payload.audience
        runCatching {
            requireNotNull(tokenAudience) {
                "Audience not present"
            }
            require(tokenAudience.contains(requiredAudience)) {
                "Audience $requiredAudience not found in token, found: $tokenAudience"
            }
            require(credentials.payload.doesNotExpireWithin(TEN_MINUTES)) {
                "Token expires soon, redirecting to login"
            }
        }.onFailure { log.error(it.message) }.getOrThrow()
        return PayloadPrincipal(credentials.payload)
    }

    private fun Payload.doesNotExpireWithin(withinMillies: Long): Boolean {
        val shiftedExpirationTime = this.expiresAt.time - withinMillies
        return System.currentTimeMillis() > shiftedExpirationTime
    }
}
