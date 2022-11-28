package no.nav.modialogin.features.bffproxyfeature

import com.nimbusds.jwt.JWT
import no.nav.common.token_client.cache.TokenCache
import no.nav.common.token_client.utils.TokenUtils
import no.nav.common.token_client.utils.TokenUtils.expiresWithin
import no.nav.modialogin.RedisConfig
import redis.clients.jedis.JedisPool
import java.util.function.Supplier
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RedisTokenCache(
    val underlying: TokenCache,
    val config: RedisConfig,
) : TokenCache {
    companion object {
        val DEFAULT_EXPIRE_BEFORE_REFRESH_MS = 30.seconds.inWholeMilliseconds
        val DEFAULT_EXPIRE_AFTER_WRITE = 60.minutes.inWholeSeconds
    }
    val pool: JedisPool = JedisPool(config.host, 6379)

    override fun getFromCacheOrTryProvider(cacheKey: String, tokenProvider: Supplier<String>): String {
        val nsCacheKey = "obocache:$cacheKey"
        return underlying.getFromCacheOrTryProvider(nsCacheKey) {
            // Normally it would exchange tokens here.
            // But first we check if we can get it from redis ;)
            pool.resource.use {redis ->
                redis.auth(config.password)
                val token = redis.get(nsCacheKey)
                val jwt: JWT? = token?.let(TokenUtils::parseJwtToken)
                if (token == null || jwt == null || expiresWithin(jwt, DEFAULT_EXPIRE_BEFORE_REFRESH_MS)) {
                    // Passthrough call to original tokenProvider, which will do the token exchange
                    val newToken = tokenProvider.get()
                    redis.setex(nsCacheKey, DEFAULT_EXPIRE_AFTER_WRITE, newToken)
                    newToken
                } else {
                    token
                }
            }
        }
    }
}