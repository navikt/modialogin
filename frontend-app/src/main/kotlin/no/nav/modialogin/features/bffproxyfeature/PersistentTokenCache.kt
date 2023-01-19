package no.nav.modialogin.features.bffproxyfeature

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT
import com.github.benmanes.caffeine.cache.Expiry
import kotlinx.coroutines.runBlocking
import no.nav.common.token_client.cache.TokenCache
import no.nav.common.token_client.utils.TokenUtils
import no.nav.common.token_client.utils.TokenUtils.expiresWithin
import no.nav.modialogin.persistence.Persistence
import no.nav.modialogin.utils.CaffeineTieredCache
import no.nav.personoversikt.common.utils.SelftestGenerator
import java.util.function.Supplier
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PersistentTokenCache(
    persistence: Persistence<String, String>,
) : TokenCache {
    companion object {
        val DEFAULT_EXPIRE_BEFORE_REFRESH_MS = 30.seconds.inWholeMilliseconds
    }

    val tieredCache = CaffeineTieredCache(
        persistence = persistence,
        selftest = SelftestGenerator.Reporter("tokencache", false),
        expirationStrategy = object : Expiry<String, String> {
            override fun expireAfterCreate(key: String, value: String, currentTime: Long): Long {
                val jwt = JWT.decode(value)
                return jwt.durationToExpiry().inWholeNanoseconds
            }

            override fun expireAfterUpdate(
                key: String,
                value: String,
                currentTime: Long,
                currentDuration: Long
            ): Long {
                val jwt = JWT.decode(value)
                return jwt.durationToExpiry().inWholeNanoseconds
            }

            override fun expireAfterRead(key: String, value: String, currentTime: Long, currentDuration: Long): Long {
                return currentDuration
            }

            private fun DecodedJWT.durationToExpiry(): Duration {
                return (expiresAt.time - System.currentTimeMillis()).milliseconds
            }
        }
    )

    override fun getFromCacheOrTryProvider(cacheKey: String, tokenProvider: Supplier<String>): String {
        return runBlocking {
            val token = tieredCache.get(cacheKey)
            val jwt = token?.let(TokenUtils::parseJwtToken)
            if (jwt == null || expiresWithin(jwt, DEFAULT_EXPIRE_BEFORE_REFRESH_MS)) {
                val newToken = tokenProvider.get()
                tieredCache.put(cacheKey, newToken)
                newToken
            } else {
                token
            }
        }
    }
}