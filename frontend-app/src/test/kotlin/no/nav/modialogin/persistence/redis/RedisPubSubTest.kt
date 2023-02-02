package no.nav.modialogin.persistence.redis

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.serialization.builtins.serializer
import no.nav.modialogin.persistence.*
import no.nav.modialogin.utils.Encoding
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisPubSubTest : RedisTestUtils.WithRedis() {

    //    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    @Test()
    fun `mottar redis-melding på kanal`() = runBlocking {
        val channel = Channel<DummySubMessage<String, DummyChannelValue>>()

        val scope = "test"

        val testUtils = RedisTestUtils.getIntegrationTestUtils(container, scope, String.serializer(), DummyChannelValue.serializer(), enablePubSub = true)

        val firstKey = "first"
        val secondKey = "second"

        val testValue = DummyChannelValue("foo", 2, false)
        val ttl = 10.minutes

        GlobalScope.launch {
            val sub = testUtils.receiveRedis.pubSub!!.startSubscribing()
            sub.onEach {
                val decodedMessage = Encoding.decode(EncodedSubMessage.serializer(), it)
                val key = Encoding.decode(String.serializer(), decodedMessage.key)
                val value = Encoding.decode(DummyChannelValue.serializer(), decodedMessage.value)
                channel.send(DummySubMessage(key, value, decodedMessage.scope, decodedMessage.expiry))
            }.collect()
        }

        val firstExpectedMessage = DummySubMessage(firstKey, testValue, "test", Clock.System.now().plus(10.minutes))
        val secondExpectedMessage = DummySubMessage(secondKey, testValue, "test", Clock.System.now().plus(10.minutes))

        delay(1000L)

        testUtils.sendRedis.doPut(firstKey, testValue, ttl)

        delay(1000L)

        container!!.restart()

        delay(1000L)
        testUtils.sendRedis.doPut(secondKey, testValue, ttl)

        val messages = channel.consumeAsFlow().take(2).toList()

        channel.close()

        val firstMessage = messages[0]
        val secondMessage = messages[1]

        Assertions.assertEquals(firstExpectedMessage.value, firstMessage.value)
        Assertions.assertEquals(firstExpectedMessage.key, firstMessage.key)
        Assertions.assertEquals(firstExpectedMessage.scope, firstMessage.scope)
        Assertions.assertTrue(firstExpectedMessage.ttl.epochSeconds - firstMessage.ttl.epochSeconds < 1.seconds.inWholeSeconds)

        Assertions.assertEquals(secondExpectedMessage.value, secondMessage.value)
        Assertions.assertEquals(secondExpectedMessage.key, secondMessage.key)
        Assertions.assertEquals(secondExpectedMessage.scope, secondMessage.scope)
        Assertions.assertTrue(secondExpectedMessage.ttl.epochSeconds - secondMessage.ttl.epochSeconds < 1.seconds.inWholeSeconds)
    }
}
