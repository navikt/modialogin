package no.nav.modialogin.features.authfeature

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT
import com.github.benmanes.caffeine.cache.Expiry
import io.ktor.util.date.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import no.nav.modialogin.RedisConfig
import no.nav.modialogin.auth.OidcClient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class RedisCaffeineSessionCache(
    redisConfig: RedisConfig,
    private val oidcClient: OidcClient
) {
    private val cache = RedisCaffeineCache(
        redisConfig = redisConfig,
        keySerializer = String.serializer(),
        valueSerializer = SessionAuthPrincipal.serializer(),
        expiry = TokenExpiry
    )

    fun get(key: String): SessionAuthPrincipal? {
        val value : SessionAuthPrincipal? = cache.get(key)
        return when {
            value == null -> null
            value.accessToken.doesNotExpireWithin(5.minutes) -> value
            value.refreshToken != null && value.accessToken.hasNotExpired() -> refreshCache(key, value.refreshToken)
            else -> invalidateCache(key)
        }
    }

    fun put(key: String, value: SessionAuthPrincipal) {
        cache.put(key, value)
    }

    private fun refreshCache(key: String, refreshToken: String): SessionAuthPrincipal {
        val newTokens = runBlocking {
            oidcClient.refreshToken(refreshToken)
        }
        val newPrincipal = SessionAuthPrincipal(
            accessToken = JWT.decode(newTokens.accessToken),
            refreshToken = newTokens.refreshToken
        )
        cache.put(key, newPrincipal)

        return newPrincipal
    }

    private fun invalidateCache(key: String): SessionAuthPrincipal? {
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

    object TokenExpiry : Expiry<String, SessionAuthPrincipal> {
        override fun expireAfterCreate(key: String, value: SessionAuthPrincipal, currentTime: Long): Long {
            return value.durationToExpiry().inWholeNanoseconds
        }

        override fun expireAfterUpdate(key: String, value: SessionAuthPrincipal, currentTime: Long, currentDuration: Long): Long {
            return value.durationToExpiry().inWholeNanoseconds
        }

        override fun expireAfterRead(key: String, value: SessionAuthPrincipal, currentTime: Long, currentDuration: Long): Long {
            return currentDuration
        }

        private fun SessionAuthPrincipal.durationToExpiry(): Duration {
            return (accessToken.expiresAt.time - System.currentTimeMillis()).milliseconds
        }
    }
}