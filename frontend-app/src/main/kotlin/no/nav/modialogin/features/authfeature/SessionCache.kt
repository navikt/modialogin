package no.nav.modialogin.features.authfeature

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.util.date.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import no.nav.modialogin.persistence.Persistence
import no.nav.modialogin.persistence.SubMessage
import no.nav.modialogin.utils.CaffeineTieredCache
import no.nav.personoversikt.common.utils.SelftestGenerator
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class SessionCache(
    private val oidcClient: OidcClient,
    persistence: Persistence<String, TokenPrincipal>,
) {
    private val cache = CaffeineTieredCache(
        persistence = persistence,
        expirationStrategy = AccessTokenExpirationStrategy,
        selftest = SelftestGenerator.Reporter("sessioncache", false),
    )

    suspend fun get(key: SessionId): TokenPrincipal? {
        val value: TokenPrincipal? = cache.get(key)
        return when {
            value == null -> null
            value.accessToken.doesNotExpireWithin(5.minutes) -> value
            value.refreshToken != null && value.accessToken.hasNotExpired() -> refreshCache(key, value.refreshToken)
            else -> invalidateCache(key)
        }
    }

    suspend fun put(key: SessionId, value: TokenPrincipal) {
        cache.put(key, value)
    }

    private suspend fun refreshCache(key: SessionId, refreshToken: String): TokenPrincipal {
        val newTokens = runBlocking(Dispatchers.IO) {
            oidcClient.refreshToken(refreshToken)
        }
        val newPrincipal = TokenPrincipal(
            accessToken = JWT.decode(newTokens.accessToken),
            refreshToken = newTokens.refreshToken
        )
        cache.put(key, newPrincipal)

        return newPrincipal
    }

    private suspend fun invalidateCache(key: SessionId): TokenPrincipal? {
        cache.invalidate(key)
        return null
    }

    private fun DecodedJWT.doesNotExpireWithin(time: Duration): Boolean {
        val expiry = this.expiresAt.time - time.inWholeMilliseconds
        return getTimeMillis() <= expiry
    }

    private fun DecodedJWT.hasNotExpired(leeway: Duration = 1.minutes): Boolean {
        return getTimeMillis() <= this.expiresAt.time + leeway.inWholeMilliseconds
    }
}
