package no.nav.modialogin

import no.nav.modialogin.common.KtorServer
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool

object RedisUtils {
    fun <T> JedisPool.useResource(password: String, block: (Jedis) -> T): T? {
        if (this.isClosed) {
            KtorServer.log.error("JedisPool is closed while trying to access it")
            return null
        }

        return runCatching {
            this.resource.use { redis ->
                redis.auth(password)
                block(redis)
            }
        }
            .onFailure { KtorServer.log.error("Redis-error", it) }
            .getOrNull()
    }
}