package no.nav.modialogin.features.bffproxyfeature

import com.nimbusds.jwt.JWT
import kotlinx.coroutines.runBlocking
import no.nav.common.token_client.cache.TokenCache
import no.nav.common.token_client.utils.TokenUtils
import no.nav.common.token_client.utils.TokenUtils.expiresWithin
import no.nav.modialogin.persistence.Persistence
import java.util.function.Supplier
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RedisTokenCache(
    private val underlying: TokenCache,
    private val persistence: Persistence<String, String>,
) : TokenCache {
    companion object {
        val DEFAULT_EXPIRE_BEFORE_REFRESH_MS = 30.seconds.inWholeMilliseconds
        val DEFAULT_EXPIRE_AFTER_WRITE = 60.minutes
    }

    override fun getFromCacheOrTryProvider(cacheKey: String, tokenProvider: Supplier<String>): String {
        return underlying.getFromCacheOrTryProvider(cacheKey) {
            // Normally it would exchange tokens here.
            // But first we check if we can get it from redis ;)
            val token = runBlocking { persistence.get(cacheKey) }
            val jwt: JWT? = token?.let(TokenUtils::parseJwtToken)
            if (jwt == null || expiresWithin(jwt, DEFAULT_EXPIRE_BEFORE_REFRESH_MS)) {
                // Passthrough call to original tokenProvider, which will do the token exchange
                val newToken = tokenProvider.get()
                runBlocking {
                    persistence.put(cacheKey, newToken, DEFAULT_EXPIRE_AFTER_WRITE)
                }
                newToken
            } else {
                token
            }
        }
    }
}