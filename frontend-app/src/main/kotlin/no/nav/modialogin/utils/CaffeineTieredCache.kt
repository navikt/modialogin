package no.nav.modialogin.utils

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import com.github.benmanes.caffeine.cache.Ticker
import kotlinx.coroutines.runBlocking
import no.nav.modialogin.persistence.Persistence
import kotlin.time.Duration.Companion.nanoseconds

class CaffeineTieredCache<KEY, VALUE>(
    val persistence: Persistence<KEY, VALUE>,
    val expirationStrategy: Expiry<KEY, VALUE>,
) {
    private val ticker = Ticker.systemTicker()
    private val localCache = Caffeine
        .newBuilder()
        .expireAfter(expirationStrategy)
        .build<KEY, VALUE>()

    init {
        runBlocking {
            val persistedValues = persistence.dump()
            localCache.putAll(persistedValues)
        }
    }

    suspend fun get(key: KEY): VALUE? {
        return localCache.get(key) {
            runBlocking {
                persistence.get(key)
            }
        }
    }

    suspend fun put(key: KEY, value: VALUE) {
        val expiry = expirationStrategy.expireAfterCreate(key, value, ticker.read()).nanoseconds
        localCache.put(key, value)
        persistence.put(key, value, expiry)
    }

    suspend fun invalidate(key: KEY) {
        persistence.remove(key)
        localCache.invalidate(key)
    }
}