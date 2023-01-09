package no.nav.modialogin.persistence.jdbc

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import no.nav.modialogin.persistence.SubMessage
import no.nav.modialogin.persistence.DummyChannelValue
import no.nav.modialogin.persistence.TestUtils
import no.nav.modialogin.persistence.TestUtils.setupSendAndReceivePostgres
import org.junit.jupiter.api.*
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import no.nav.modialogin.utils.Encoding.encode

class JdbcPubSubTest : TestUtils.WithPostgres {
    @Test()
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `mottar postgres-melding på kanal`() = runBlocking {
        val scope = "test"
        val (sendPostgres, receivePostgres) = setupSendAndReceivePostgres(scope, String.serializer(), DummyChannelValue.serializer(), enablePubSub = true)

        val testKey = "TEST_KEY"
        val testValue = DummyChannelValue("foo", 2, false)
        val ttl = 10.minutes
        val subscription = receivePostgres.pubSub!!.startSubscribing()

        sendPostgres.doPut(testKey, testValue, ttl)

        val firstMessage = subscription.first()

        val expectedMessage = SubMessage(testKey, encode(DummyChannelValue.serializer(), testValue), LocalDateTime.now().plusSeconds(ttl.inWholeSeconds))

        receivePostgres.pubSub!!.stopSubscribing()

        Assertions.assertEquals(expectedMessage.value, firstMessage.value)
        Assertions.assertEquals("$scope:${expectedMessage.key}", firstMessage.key)
        Assertions.assertTrue(Duration.between(expectedMessage.ttl, firstMessage.ttl) < 1.seconds.toJavaDuration())
    }
}
