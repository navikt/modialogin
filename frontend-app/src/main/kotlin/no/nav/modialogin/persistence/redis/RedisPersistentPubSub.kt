package no.nav.modialogin.persistence.redis

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.modialogin.Logging
import no.nav.modialogin.Logging.log
import no.nav.modialogin.persistence.PersistentPubSub
import no.nav.modialogin.persistence.SubMessage
import no.nav.modialogin.utils.AuthJedisPool
import redis.clients.jedis.JedisPubSub
import java.time.LocalDateTime

class RedisPersistentPubSub(
    chanelName: String,
    private val redisPool: AuthJedisPool,
) : PersistentPubSub(chanelName) {
    private var job: Job? = null
    private var channel = Channel<SubMessage>()

    override fun startSubscribing(): Flow<SubMessage> {
        doStart()
        return channel.consumeAsFlow()
    }

    override fun stopSubscribing() {
        channel.close()
        doStop()
        channel = Channel()
    }

    override suspend fun publishMessage(key: String, value: String, expiry: LocalDateTime) {
        val message = Json.encodeToString(RedisEncodedMessage(key, value, expiry))
        redisPool.useResource {
            it.publish(channelName, message)
        }
    }

    private val subscriber = object : JedisPubSub() {
        override fun onMessage(channelName: String?, message: String?) {
            if (channelName == chanelName && message != null) {
                try {
                    val (encodedKey, encodedValue, expiry) = Json.decodeFromString<RedisEncodedMessage>(message)
                    runBlocking(Dispatchers.IO) {
                        channel.send(SubMessage(encodedKey, encodedValue, expiry))
                    }
                } catch (e: Exception) {
                    Logging.log.error("Failed to parse Redis Sub message: ", e)
                }
            }
            super.onMessage(channelName, message)
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
