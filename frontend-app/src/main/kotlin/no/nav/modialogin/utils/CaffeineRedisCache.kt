package no.nav.modialogin.utils

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import com.github.benmanes.caffeine.cache.Ticker
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import no.nav.modialogin.RedisConfig
import no.nav.modialogin.utils.RedisUtils.useRedis
import kotlin.time.Duration.Companion.nanoseconds

class CaffeineRedisCache<KEY, VALUE>(
    val redisConfig: RedisConfig,
    val expirationStrategy: Expiry<KEY, VALUE>,
    val keySerializer: KSerializer<KEY>,
    val valueSerializer: KSerializer<VALUE>,
) {
    private val ticker = Ticker.systemTicker()
    private val localCache = Caffeine
        .newBuilder()
        .expireAfter(expirationStrategy)
        .build<KEY, VALUE>()

    fun get(key: KEY): VALUE? {
        return localCache.get(key) {
            val value: String? = useRedis { redis ->
                redis.get(encode(keySerializer, key))
            }
            value?.let { decode(valueSerializer, it) }
        }
    }

    fun put(key: KEY, value: VALUE) {
        val expiry = expirationStrategy.expireAfterCreate(key, value, ticker.read()).nanoseconds
        localCache.put(key, value)
        useRedis { redis ->
            redis.setex(
                encode(keySerializer, key),
                expiry.inWholeSeconds,
                encode(valueSerializer, value),
            )
        }
    }

    fun invalidate(key: KEY) {
        useRedis { redis ->
            redis.del(encode(keySerializer, key))
        }
        localCache.invalidate(key)
    }

    companion object {
        private fun <T> encode(serializer: KSerializer<T>, value: T): String {
            if (serializer == String.serializer()) return value as String

            return Json.encodeToString(serializer, value)
        }

        private fun <T> decode(serializer: KSerializer<T>, value: String): T {
            if (serializer == String.serializer()) return value as T

            return Json.decodeFromString(serializer, value)
        }
    }
}