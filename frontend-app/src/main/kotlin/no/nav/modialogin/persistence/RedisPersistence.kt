package no.nav.modialogin.persistence

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import no.nav.modialogin.utils.AuthJedisPool
import kotlin.time.Duration

class RedisPersistence<KEY, VALUE>(
    scope: String,
    private val redisPool: AuthJedisPool,
    private val keySerializer: KSerializer<KEY>,
    private val valueSerializer: KSerializer<VALUE>,
) : Persistence<KEY, VALUE>(scope) {
    override suspend fun doGet(key: KEY): VALUE? {
        val value: String? = redisPool.useResource {
            it.get("$scope-${encode(keySerializer, key)}")
        }
        return value?.let { decode(valueSerializer, it) }
    }

    override suspend fun doPut(key: KEY, value: VALUE, expiry: Duration?) {
        redisPool.useResource {
            if (expiry != null) {
                it.setex(
                    "$scope-${encode(keySerializer, key)}",
                    expiry.inWholeSeconds,
                    encode(valueSerializer, value)
                )
            } else {
                it.set(
                    "$scope-${encode(keySerializer, key)}",
                    encode(valueSerializer, value)
                )
            }
        }
    }

    override suspend fun doRemove(key: KEY) {
        redisPool.useResource {
            it.del("$scope-${encode(keySerializer, key)}")
        }
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