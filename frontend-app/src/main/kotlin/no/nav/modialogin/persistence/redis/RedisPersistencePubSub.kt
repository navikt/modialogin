package no.nav.modialogin.persistence.redis

import io.ktor.client.plugins.*
import kotlinx.coroutines.*
import no.nav.modialogin.Logging.log
import no.nav.modialogin.PubSubConfig
import no.nav.modialogin.persistence.PersistencePubSub
import no.nav.modialogin.utils.AuthJedisPool
import no.nav.modialogin.utils.RedisConfig
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPubSub

class RedisPersistencePubSub(
    pubSubConfig: PubSubConfig,
    private val redisConfig: RedisConfig,
) : PersistencePubSub(pubSubConfig, "redis") {
    private val publishPool = AuthJedisPool(redisConfig)

    override suspend fun publishData(data: String): Result<*> {
        var result: Result<Long?>
        var counter = 0

        do {
            result = publishPool.useResource {
                it.publish(pubSubConfig.channelName, data)
            }
            if (result.isFailure) {
                counter++
                delay(pubSubConfig.pubRetryInterval)
            }
        } while (result.isFailure && counter <= pubSubConfig.pubMaxRetries)

        return result
    }

    private val subscriber = object : JedisPubSub() {
        override fun onMessage(channelName: String?, message: String?) {
            if (pubSubConfig.channelName == channelName && message != null) {
                try {
                    runBlocking(Dispatchers.IO) {
                        channel.send(message)
                    }
                } catch (e: Exception) {
                    log.error("Failed to parse Redis Sub message", e)
                }
            }
            super.onMessage(channelName, message)
        }
    }

    override fun subscribe(retryInterval: Long) {
        while (running) {
            try {
                val jedis = Jedis(redisConfig.host, redisConfig.port)
                jedis.auth(redisConfig.password)
                jedis.subscribe(subscriber, pubSubConfig.channelName)
            } catch (e: Exception) {
                log.error("Encountered an exception when subscribing to Redis", e)
            }
            Thread.sleep(retryInterval)
        }
    }
}
