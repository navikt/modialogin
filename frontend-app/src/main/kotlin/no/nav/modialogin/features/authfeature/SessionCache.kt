package no.nav.modialogin.features.authfeature

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.util.date.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import no.nav.modialogin.RedisConfig
import no.nav.modialogin.auth.OidcClient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class SessionCache(
    redisConfig: RedisConfig,
    private val oidcClient: OidcClient,
) {
    val cache = CaffeineRedisCache(
        redisConfig = redisConfig,
        expirationStrategy = AccessTokenExpirationStrategy,
        keySerializer = String.serializer(),
        valueSerializer = TokenPrincipal.serializer()
    )

    fun get(key: SessionId): TokenPrincipal? {
        val value: TokenPrincipal? = cache.get(key)
        return when {
            value == null -> null
            value.accessToken.doesNotExpireWithin(5.minutes) -> value
            value.refreshToken != null && value.accessToken.hasNotExpired() -> refreshCache(key, value.refreshToken)
            else -> invalidateCache(key)
        }
    }

    fun put(key: SessionId, value: TokenPrincipal) {
        cache.put(key, value)
    }

    fun refreshCache(key: SessionId, refreshToken: String): TokenPrincipal {
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

    fun invalidateCache(key: SessionId): TokenPrincipal? {
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