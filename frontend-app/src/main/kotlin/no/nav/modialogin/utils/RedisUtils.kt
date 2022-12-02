package no.nav.modialogin.utils

import no.nav.modialogin.Logging.log
import no.nav.personoversikt.common.utils.EnvUtils.getRequiredConfig
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool

object RedisUtils {
    private val password = getRequiredConfig("REDIS_PASSWORD")
    private val redisPool: JedisPool by lazy {
        JedisPool(getRequiredConfig("REDIS_HOST"), 6379)
    }

    fun <T> useRedis(block: (Jedis) -> T): T? {
        if (redisPool.isClosed) {
            log.error("JedisPool is closed while trying to access it")
            return null
        }

        return runCatching {
            redisPool.resource.use { redis ->
                redis.auth(password)
                block(redis)
            }
        }
            .onFailure { log.error("Redis-error", it) }
            .getOrNull()
    }
}