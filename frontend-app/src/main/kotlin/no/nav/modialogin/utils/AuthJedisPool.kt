package no.nav.modialogin.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.modialogin.Logging
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool

class AuthJedisPool(private val redisConfig: RedisConfig) {
    private val pool = JedisPool(redisConfig.host, redisConfig.port)

    suspend fun <T> useResource(block: (Jedis) -> T): Result<T?> {
        return withContext(Dispatchers.IO) {
            if (pool.isClosed) {
                Logging.log.error("JedisPool is closed while trying to access it")
                Result.failure(IllegalStateException("RedisPool is closed"))
            } else {
                runCatching {
                    pool.resource.use {
                        it.auth(redisConfig.password)
                        block(it)
                    }
                }
            }
        }.onFailure {
            Logging.log.error("Redis-error", it)
        }
    }
}