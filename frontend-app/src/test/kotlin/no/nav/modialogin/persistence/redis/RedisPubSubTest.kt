package no.nav.modialogin.persistence.redis

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.serializer
import no.nav.modialogin.persistence.DummyChannelValue
import no.nav.modialogin.persistence.SubMessage
import no.nav.modialogin.persistence.TestUtils
import no.nav.modialogin.persistence.TestUtils.setupSendAndReceiveRedis
import no.nav.modialogin.utils.Encoding.encode
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class RedisPubSubTest : TestUtils.WithRedis {
    @Test()
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `mottar redis-melding på kanal`() = runBlocking {
        val scope = "test"
        val (sendRedis, receiveRedis) = setupSendAndReceiveRedis(scope, String.serializer(), DummyChannelValue.serializer(), enablePubSub = true)

        val testKey = "TEST_KEY"
        val testValue = DummyChannelValue("foo", 9000, false)
        val ttl = 10.seconds
        val subscription = receiveRedis.pubSub!!.startSubscribing()

        sendRedis.doPut(testKey, testValue, ttl)

        val firstMessage = subscription.first()
        val expectedMessage = SubMessage(testKey, encode(DummyChannelValue.serializer(), testValue), LocalDateTime.now().plusSeconds(ttl.inWholeSeconds))

        receiveRedis.pubSub!!.stopSubscribing()

        Assertions.assertEquals(expectedMessage.key, firstMessage.key)
        Assertions.assertEquals(expectedMessage.value, firstMessage.value)
        Assertions.assertTrue(Duration.between(expectedMessage.ttl, firstMessage.ttl) < 1.seconds.toJavaDuration())
    }
}
