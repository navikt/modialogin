package no.nav.modialogin.features.authfeature

import com.fasterxml.jackson.databind.ser.std.StringSerializer
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import com.github.benmanes.caffeine.cache.Ticker
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import no.nav.modialogin.RedisConfig
import no.nav.modialogin.common.KtorServer
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import kotlin.time.Duration.Companion.nanoseconds

class RedisCaffeineCache<KEY : Any, VALUE : Any>(
    private val redisConfig: RedisConfig,
    private val keySerializer: KSerializer<KEY>,
    private val valueSerializer: KSerializer<VALUE>,
    private val expiry: Expiry<KEY, VALUE>
) {
    private val redisPool = JedisPool(redisConfig.host, 6379)
    private val cacheTicker = Ticker.systemTicker()
    private val cache = Caffeine
        .newBuilder()
        .expireAfter(expiry)
        .build<KEY, VALUE>()

    fun get(key: KEY): VALUE? {
        val localValue = cache.getIfPresent(key)
        if (localValue != null) {
            return localValue
        }

        val keyString: String = encode(keySerializer, key)
        val valueString: String? = redisPool.useSafely { redis ->
            redis.get(keyString)
        }
        if (valueString != null) {
            val value: VALUE = decode(valueSerializer, valueString)
            cache.put(key, value)
            return value
        }

        return null
    }

    fun put(key: KEY, value: VALUE) {
        cache.put(key, value)

        val keyString: String = encode(keySerializer, key)
        val valueString: String = encode(valueSerializer, value)
        val expiresAfter = expiry.expireAfterCreate(key, value, cacheTicker.read()).nanoseconds

        redisPool.useSafely { redis ->
            redis.setex(keyString, expiresAfter.inWholeSeconds, valueString)
        }
    }

    fun invalidate(key: KEY) {
        cache.invalidate(key)

        val keyString: String = encode(keySerializer, key)
        redisPool.useSafely { redis ->
            redis.del(keyString)
        }
    }

    private fun <R> JedisPool.useSafely(block: (Jedis) -> R): R? {
        return this.resource.use { redis ->
            runCatching {
                redis.auth(redisConfig.password)
                block(redis)
            }.onFailure {
                KtorServer.log.error("Redis error", it)
            }.getOrNull()
        }
    }

}

private fun <T> encode(serializer: KSerializer<T>, value: T): String {
    return when(serializer) {
        String.serializer() -> value as String
        else -> Json.encodeToString(serializer, value)
    }
}

private fun <T> decode(serializer: KSerializer<T>, value: String): T {
    return when(serializer) {
        String.serializer() -> value as T
        else -> Json.decodeFromString(serializer, value)
    }
}
