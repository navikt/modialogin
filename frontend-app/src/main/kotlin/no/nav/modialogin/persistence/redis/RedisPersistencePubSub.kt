package no.nav.modialogin.persistence.redis

import kotlinx.coroutines.*
import no.nav.modialogin.Logging.log
import no.nav.modialogin.persistence.PersistencePubSub
import no.nav.modialogin.utils.AuthJedisPool
import no.nav.modialogin.utils.RedisConfig
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPubSub

class RedisPersistencePubSub(
    channelName: String,
    private val redisConfig: RedisConfig,
) : PersistencePubSub(channelName, "redis") {
    private val publishPool = AuthJedisPool(redisConfig)

    override suspend fun publishData(data: String): Result<*> {
        return publishPool.useResource {
            it.publish(channelName, data)
        }
    }

    private val subscriber = object : JedisPubSub() {
        override fun onMessage(messageChannel: String?, message: String?) {
            if (channelName == messageChannel && message != null) {
                try {
                    runBlocking(Dispatchers.IO) {
                        channel.send(message)
                    }
                } catch (e: Exception) {
                    log.error("Failed to parse Redis Sub message", e)
                }
            }
            super.onMessage(messageChannel, message)
        }
    }

    override fun subscribe(retryInterval: Long) {
        while (running) {
            try {
                val jedis = Jedis(redisConfig.host, redisConfig.port)
                jedis.auth(redisConfig.password)
                jedis.subscribe(subscriber, channelName)
            } catch (e: Exception) {
                log.error("Encountered an exception when subscribing to Redis", e)
            }
            Thread.sleep(retryInterval)
        }
    }
}
