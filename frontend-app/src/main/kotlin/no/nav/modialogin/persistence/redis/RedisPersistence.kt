package no.nav.modialogin.persistence.redis

import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import no.nav.modialogin.persistence.EncodedSubMessage
import no.nav.modialogin.persistence.Persistence
import no.nav.modialogin.utils.*
import no.nav.modialogin.utils.Encoding.decode
import no.nav.modialogin.utils.Encoding.encode
import no.nav.modialogin.utils.KotlinUtils.filterNotNull
import no.nav.personoversikt.common.utils.SelftestGenerator
import redis.clients.jedis.params.ScanParams
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class RedisPersistence<KEY, VALUE>(
    scope: String,
    keySerializer: KSerializer<KEY>,
    valueSerializer: KSerializer<VALUE>,
    private val redisPool: AuthJedisPool,
    pubSub: RedisPersistencePubSub? = null
) : Persistence<KEY, VALUE>(scope, keySerializer, valueSerializer, pubSub) {
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

    override suspend fun doPut(key: KEY, value: VALUE, ttl: Duration): Result<*> {
        val encodedKey = encode(keySerializer, key)
        val encodedValue = encode(valueSerializer, value)
        val ttlInSeconds = ttl.inWholeSeconds
        return redisPool.useResource {
            it.setex(
                "$scope:$encodedKey",
                ttlInSeconds,
                encodedValue
            )
            if (pubSub == null) return@useResource
            runBlocking {
                val expiry = Clock.System.now().plus(ttlInSeconds.seconds).toLocalDateTime(TimeZone.currentSystemDefault())
                val data = EncodedSubMessage(scope, encodedKey, encodedValue, expiry)
                pubSub.publishData(encode(EncodedSubMessage.serializer(), data))
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

    override suspend fun doSize(): Long {
        return redisPool.useResource { redis ->
            val params = ScanParams().count(100).match("$scope:*")
            var cursor = ScanParams.SCAN_POINTER_START
            var count = 0L
            do {
                val result = redis.scan(cursor, params)
                count += result.result.size
                cursor = result.cursor
            } while (cursor != "0")

            count
        }.getOrNull() ?: -1L
    }

    private suspend fun ping() {
        redisPool.useResource { it.ping() }
            .onSuccess { selftest.reportOk() }
            .onFailure(selftest::reportError)
    }
}
