package no.nav.modialogin.persistence.redis

import kotlinx.coroutines.*
import kotlinx.serialization.KSerializer
import no.nav.modialogin.persistence.Persistence
import no.nav.modialogin.utils.*
import no.nav.modialogin.utils.Encoding.decode
import no.nav.modialogin.utils.Encoding.encode
import no.nav.modialogin.utils.KotlinUtils.filterNotNull
import no.nav.personoversikt.common.utils.SelftestGenerator
import redis.clients.jedis.params.ScanParams
import java.time.LocalDateTime
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class RedisPersistence<KEY, VALUE>(
    scope: String,
    private val redisPool: AuthJedisPool,
    private val keySerializer: KSerializer<KEY>,
    private val valueSerializer: KSerializer<VALUE>,
    pubSub: RedisPersistencePubSub<KEY, VALUE>? = null
) : Persistence<KEY, VALUE>(scope, pubSub) {
    private val selftest = SelftestGenerator.Reporter("Redis", critical = false)

    init {
        fixedRateTimer("Redis check", daemon = true, initialDelay = 0, period = 10.seconds.inWholeMilliseconds) {
            runBlocking(Dispatchers.IO) { ping() }
        }
    }

    override suspend fun doGet(key: KEY): VALUE? {
        return redisPool
            .useResource { it.get("$scope:${encode(keySerializer, key)}") }
            .filterNotNull()
            .map { value -> decode(valueSerializer, value) }
            .getOrNull()
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

            if (keys.isEmpty()) {
                emptyMap()
            } else {
                val values = it.mget(*keys.toTypedArray())
                keys.zip(values).toMap()
            }
        }.getOrNull() ?: emptyMap()

        return values
            .mapKeys { decode(keySerializer, it.key) }
            .mapValues { decode(valueSerializer, it.value) }
    }

    override suspend fun doPut(key: KEY, value: VALUE, ttl: Duration) {
        redisPool.useResource<Unit> {
            val encodedKey = encode(keySerializer, key)
            val encodedValue = encode(valueSerializer, value)
            val ttlInSeconds = ttl.inWholeSeconds
            it.setex(
                "$scope:$encodedKey",
                ttlInSeconds,
                encodedValue
            )
            runBlocking {
                val expiry = LocalDateTime.now().plusSeconds(ttl.inWholeSeconds)
                pubSub?.publishMessage(scope, encodedKey, encodedValue, expiry)
            }
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

    private suspend fun ping() {
        redisPool.useResource { it.ping() }
            .onSuccess { selftest.reportOk() }
            .onFailure(selftest::reportError)
    }
}
