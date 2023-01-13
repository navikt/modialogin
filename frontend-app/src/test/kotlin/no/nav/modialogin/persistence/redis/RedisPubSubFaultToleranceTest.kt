package no.nav.modialogin.persistence.redis

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.builtins.serializer
import no.nav.modialogin.persistence.DummyChannelValue
import no.nav.modialogin.persistence.DummySubMessage
import no.nav.modialogin.persistence.EncodedSubMessage
import no.nav.modialogin.persistence.TestUtils
import no.nav.modialogin.utils.Encoding
import no.nav.modialogin.utils.FlowTransformer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

class RedisPubSubFaultToleranceTest : TestUtils.WithRedisFixed {

    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @Test()
    fun `klarer å hente seg inn selv om redis instansen går ned`() = runBlocking {
        val scope = "test"
        val (sendRedis, receiveRedis) = TestUtils.setupSendAndReceiveRedis(scope, String.serializer(), DummyChannelValue.serializer(), TestUtils.WithRedisFixed.container,  enablePubSub = true)

        val testKey = "TEST_KEY"
        val testValue = DummyChannelValue("foo", 9000, false)
        val ttl = 10.seconds
        val subscription = receiveRedis.pubSub!!.startSubscribing()
        val expectedMessage = DummySubMessage(testKey, testValue, "test", Clock.System.now().plus(10.seconds))

        TestUtils.WithRedis.stopContainer()
        delay(2000L)
        TestUtils.WithRedis.startContainer()
        delay(5000L)

        sendRedis.doPut(testKey, testValue, ttl)

        val firstMessage = FlowTransformer.mapData(subscription, EncodedSubMessage.serializer()) {
            val key = Encoding.decode(String.serializer(), it.key)
            val value = Encoding.decode(DummyChannelValue.serializer(), it.value)
            DummySubMessage(key, value, it.scope, it.expiry)
        }.first()

        receiveRedis.pubSub!!.stopSubscribing()

        Assertions.assertEquals(expectedMessage.value, firstMessage.value)
        Assertions.assertEquals(expectedMessage.key, firstMessage.key)
        Assertions.assertEquals(expectedMessage.scope, firstMessage.scope)
        Assertions.assertTrue(expectedMessage.ttl.epochSeconds - firstMessage.ttl.epochSeconds < 1.seconds.inWholeSeconds)
    }
}