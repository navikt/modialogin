package no.nav.modialogin.persistence.redis

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.modialogin.Logging
import no.nav.modialogin.Logging.log
import no.nav.modialogin.persistence.EncodedSubMessage
import no.nav.modialogin.persistence.PersistentPubSub
import no.nav.modialogin.persistence.SubMessage
import no.nav.modialogin.utils.AuthJedisPool
import no.nav.modialogin.utils.Encoding.decode
import redis.clients.jedis.JedisPubSub
import java.time.LocalDateTime

class RedisPersistencePubSub<KEY, VALUE>(
    channelName: String,
    keySerializer: KSerializer<KEY>,
    valueSerializer: KSerializer<VALUE>,
    private val redisPool: AuthJedisPool,
) : PersistentPubSub<KEY, VALUE>(channelName, keySerializer, valueSerializer) {
    private var job: Job? = null
    private var channel = Channel<SubMessage<KEY, VALUE>>()

    override fun startSubscribing(): Flow<SubMessage<KEY, VALUE>> {
        doStart()
        return channel.consumeAsFlow()
    }

    override fun stopSubscribing() {
        channel.close()
        doStop()
        channel = Channel()
    }

    override suspend fun publishMessage(scope: String, key: String, value: String, expiry: LocalDateTime) {
        val message = Json.encodeToString(EncodedSubMessage(scope, key, value, expiry))
        redisPool.useResource {
            it.publish(channelName, message)
        }
    }

    private val subscriber = object : JedisPubSub() {
        override fun onMessage(messageChannel: String?, message: String?) {
            if (channelName == messageChannel && message != null) {
                try {
                    val (scope, encodedKey, encodedValue, expiry) = Json.decodeFromString<EncodedSubMessage>(message)
                    val key = decode(keySerializer, encodedKey)
                    val value = decode(valueSerializer, encodedValue)
                    runBlocking(Dispatchers.IO) {
                        channel.send(SubMessage(scope, key, value, expiry))
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
