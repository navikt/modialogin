package no.nav.modialogin.persistence

import kotlinx.serialization.KSerializer
import no.nav.modialogin.utils.AuthJedisPool
import no.nav.modialogin.utils.Encoding.encode
import no.nav.modialogin.utils.Encoding.decode
import redis.clients.jedis.params.ScanParams
import kotlin.time.Duration

class RedisPersistence<KEY, VALUE>(
    scope: String,
    private val redisPool: AuthJedisPool,
    private val keySerializer: KSerializer<KEY>,
    private val valueSerializer: KSerializer<VALUE>,
) : Persistence<KEY, VALUE>(scope) {
    override suspend fun doGet(key: KEY): VALUE? {
        val value: String? = redisPool.useResource {
            it.get("$scope:${encode(keySerializer, key)}")
        }
        return value?.let { decode(valueSerializer, it) }
    }

    override suspend fun doDump(): Map<KEY, VALUE> {
        val values = redisPool.useResource {
            val params = ScanParams().count(100).match("$scope:*")
            var cursor = ScanParams.SCAN_POINTER_START
            val keys = mutableListOf<String>()
            do {
                val result = it.scan(cursor, params)
                keys.addAll(result.result)
                cursor = result.cursor
            } while (cursor != "0")

            val values = it.mget(*keys.toTypedArray())
            keys.zip(values).toMap()
        } ?: emptyMap()

        return values
            .mapKeys { decode(keySerializer, it.key) }
            .mapValues { decode(valueSerializer, it.value) }
    }

    override suspend fun doPut(key: KEY, value: VALUE, ttl: Duration) {
        redisPool.useResource {
            it.setex(
                "$scope:${encode(keySerializer, key)}",
                ttl.inWholeSeconds,
                encode(valueSerializer, value)
            )
        }
    }

    override suspend fun doRemove(key: KEY) {
        redisPool.useResource {
            it.del("$scope:${encode(keySerializer, key)}")
        }
    }

    override suspend fun doClean() {
        // Automatically done by redis
    }
}