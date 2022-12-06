package no.nav.modialogin.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.modialogin.Logging
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool

class AuthJedisPool(host: String, private val password: String) {
    private val pool = JedisPool(host, 6379)
    suspend fun <T> useResource(block: (Jedis) -> T): T? {
        return withContext(Dispatchers.IO) {
            if (pool.isClosed) {
                Logging.log.error("JedisPool is closed while trying to access it")
                null
            } else {
                runCatching {
                    pool.resource.use {
                        it.auth(password)
                        block(it)
                    }
                }
                    .onFailure { Logging.log.error("Redis-error", it) }
                    .getOrNull()
            }
        }
    }
}