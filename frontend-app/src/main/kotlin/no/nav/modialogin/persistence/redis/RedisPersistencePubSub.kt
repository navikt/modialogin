package no.nav.modialogin.persistence.redis

import kotlinx.coroutines.*
import no.nav.modialogin.Logging.log
import no.nav.modialogin.persistence.PersistencePubSub
import no.nav.modialogin.utils.AuthJedisPool
import redis.clients.jedis.JedisPubSub

class RedisPersistencePubSub(
    channelName: String,
    private val redisPool: AuthJedisPool,
) : PersistencePubSub(channelName, "redis") {
    override suspend fun publishData(data: String) {
        redisPool.useResource {
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
                    log.error("Failed to parse Redis Sub message: ", e)
                }
            }
            super.onMessage(messageChannel, message)
        }
    }

    override suspend fun subscribe(retryInterval: Long) {
        while (running) {
            val exitStatus = redisPool.useResource {
                it.subscribe(subscriber, channelName)
            }
            if (exitStatus.isFailure) {
                delay(retryInterval)
            }
        }
    }
}
