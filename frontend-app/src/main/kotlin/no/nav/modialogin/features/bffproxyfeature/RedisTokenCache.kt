package no.nav.modialogin.features.bffproxyfeature

import com.nimbusds.jwt.JWT
import no.nav.common.token_client.cache.TokenCache
import no.nav.common.token_client.utils.TokenUtils
import no.nav.common.token_client.utils.TokenUtils.expiresWithin
import no.nav.modialogin.utils.RedisUtils.useRedis
import java.util.function.Supplier
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RedisTokenCache(val underlying: TokenCache) : TokenCache {
    companion object {
        val DEFAULT_EXPIRE_BEFORE_REFRESH_MS = 30.seconds.inWholeMilliseconds
        val DEFAULT_EXPIRE_AFTER_WRITE = 60.minutes.inWholeSeconds
    }

    override fun getFromCacheOrTryProvider(cacheKey: String, tokenProvider: Supplier<String>): String {
        return underlying.getFromCacheOrTryProvider(cacheKey) {
            // Normally it would exchange tokens here.
            // But first we check if we can get it from redis ;)
            val token = useRedis { redis -> redis.get(cacheKey) }
            val jwt: JWT? = token?.let(TokenUtils::parseJwtToken)
            if (jwt == null || expiresWithin(jwt, DEFAULT_EXPIRE_BEFORE_REFRESH_MS)) {
                // Passthrough call to original tokenProvider, which will do the token exchange
                val newToken = tokenProvider.get()
                useRedis { redis ->
                    redis.setex(cacheKey, DEFAULT_EXPIRE_AFTER_WRITE, newToken)
                }
                newToken
            } else {
                token
            }
        }
    }
}