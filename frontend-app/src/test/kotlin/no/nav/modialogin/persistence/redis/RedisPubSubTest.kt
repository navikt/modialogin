package no.nav.modialogin.persistence.redis

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.serialization.builtins.serializer
import no.nav.modialogin.persistence.*
import no.nav.modialogin.utils.Encoding
import no.nav.modialogin.utils.FlowTransformer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisPubSubTest : RedisTestUtils.WithRedis() {

    @Test()
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `mottar redis-melding på kanal`() = runBlocking {
        val scope = "test"

        val testUtils = RedisTestUtils.getIntegrationTestUtils(container, scope, String.serializer(), DummyChannelValue.serializer(), enablePubSub = true)
        val subscription = testUtils.receiveRedis.pubSub!!.startSubscribing()

        val testKey = "TEST_KEY"
        val testValue = DummyChannelValue("foo", 9000, false)
        val ttl = 10.seconds
        val expectedMessage = DummySubMessage(testKey, testValue, "test", Clock.System.now().plus(10.seconds))

        testUtils.sendRedis.doPut(testKey, testValue, ttl)

        val firstMessage = FlowTransformer.mapData(subscription, EncodedSubMessage.serializer()) {
            val key = Encoding.decode(String.serializer(), it.key)
            val value = Encoding.decode(DummyChannelValue.serializer(), it.value)
            DummySubMessage(key, value, it.scope, it.expiry)
        }.first()

        testUtils.receiveRedis.pubSub!!.stopSubscribing()

        Assertions.assertEquals(expectedMessage.value, firstMessage.value)
        Assertions.assertEquals(expectedMessage.key, firstMessage.key)
        Assertions.assertEquals(expectedMessage.scope, firstMessage.scope)
        Assertions.assertTrue(expectedMessage.ttl.epochSeconds - firstMessage.ttl.epochSeconds < 1.seconds.inWholeSeconds)
    }

    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    @Test()
    fun `klarer å hente seg inn selv om redis instansen går ned`() = runBlocking {
        val scope = "test"

        val testUtils = RedisTestUtils.getIntegrationTestUtils(container, scope, String.serializer(), DummyChannelValue.serializer(), enablePubSub = true)
        val subscription = testUtils.receiveRedis.pubSub!!.startSubscribing(1000L)

        val testKey = "TEST_KEY"
        val testValue = DummyChannelValue("foo", 9000, false)
        val ttl = 10.seconds
        val expectedMessage = DummySubMessage(testKey, testValue, "test", Clock.System.now().plus(10.seconds))

        container!!.restart()

        delay(5000L) // Venter på at subscriberen skal reconnecte

        testUtils.sendRedis.doPut(testKey, testValue, ttl)

        val firstMessage = FlowTransformer.mapData(subscription, EncodedSubMessage.serializer()) {
            val key = Encoding.decode(String.serializer(), it.key)
            val value = Encoding.decode(DummyChannelValue.serializer(), it.value)
            DummySubMessage(key, value, it.scope, it.expiry)
        }.first()

        testUtils.receiveRedis.pubSub!!.stopSubscribing()

        Assertions.assertEquals(expectedMessage.value, firstMessage.value)
        Assertions.assertEquals(expectedMessage.key, firstMessage.key)
        Assertions.assertEquals(expectedMessage.scope, firstMessage.scope)
        Assertions.assertTrue(expectedMessage.ttl.epochSeconds - firstMessage.ttl.epochSeconds < 1.seconds.inWholeSeconds)
    }
}
