package no.nav.modialogin.persistence.redis

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import no.nav.modialogin.Logging
import no.nav.modialogin.Logging.log
import no.nav.modialogin.persistence.PersistencePubSub
import no.nav.modialogin.utils.AuthJedisPool
import redis.clients.jedis.JedisPubSub

class RedisPersistencePubSub(
    channelName: String,
    private val redisPool: AuthJedisPool,
) : PersistencePubSub(channelName) {
    private var job: Job? = null
    private var channel = Channel<String>()

    override fun startSubscribing(): Flow<String> {
        doStart()
        return channel.consumeAsFlow()
    }

    override fun stopSubscribing() {
        channel.close()
        doStop()
        channel = Channel()
    }

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

    private fun doStop() {
        log.info("Stopping redis subscriber on channel '$channelName'")
        subscriber.unsubscribe()
        job?.cancel()
    }

    private fun doStart() {
        log.info("starting redis subscriber on channel '$channelName'")
        job = GlobalScope.launch { subscribe() }
    }

    private suspend fun subscribe() {
        try {
            redisPool.useResource {
                it.subscribe(subscriber, channelName)
            }
        } catch (e: Exception) {
            Logging.log.error("Error when subscribing to Redis pub/sub", e)
        }
    }
}
