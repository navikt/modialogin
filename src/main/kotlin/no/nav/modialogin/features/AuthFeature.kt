package no.nav.modialogin.features

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.http.auth.*
import no.nav.modialogin.log
import java.net.URL
import java.util.concurrent.TimeUnit

object AuthFeature {
    private const val cookieName = "ID_token"

    data class Config(
        var jwksUrl: String = "",
        var acceptedAudience: String = ""
    )

    class SubjectPrincipal(val subject: String) : Principal

    fun Application.installAuthFeature(block: Config.() -> Unit) {
        val config = Config().apply(block)
        install(Authentication) {
            jwt {
                authHeader(AuthFeature::useJwtFromCookie)
                verifier(makeJwkProvider(config.jwksUrl))
                validate { validateJWT(it, config.acceptedAudience) }
            }
        }
    }

    private fun useJwtFromCookie(call: ApplicationCall): HttpAuthHeader? {
        return try {
            val token = call.request.cookies[cookieName]
            parseAuthorizationHeader("Bearer $token")
        } catch (ex: Throwable) {
            log.warn("Could not get JWT from cookie '$cookieName'", ex)
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
        requireNotNull(tokenAudience) {
            "Audience not present"
        }
        require(tokenAudience.contains(requiredAudience)) {
            "Audience $requiredAudience not found in token, found: $tokenAudience"
        }
        return SubjectPrincipal(credentials.payload.subject)
    }
}
