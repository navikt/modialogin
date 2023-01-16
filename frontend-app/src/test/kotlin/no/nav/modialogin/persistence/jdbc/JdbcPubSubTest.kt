package no.nav.modialogin.persistence.jdbc

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.builtins.serializer
import no.nav.modialogin.persistence.*
import no.nav.modialogin.persistence.TestUtils.setupSendAndReceivePostgres
import no.nav.modialogin.utils.Encoding.decode
import no.nav.modialogin.utils.FlowTransformer
import org.junit.jupiter.api.*
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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

        val expectedMessage = DummySubMessage(testKey, testValue, "test", Clock.System.now().plus(10.minutes))

        delay(2000L)
        sendPostgres.doPut(testKey, testValue, ttl)
        val firstMessage = FlowTransformer.mapData(subscription, EncodedSubMessage.serializer()) {
            val key = decode(String.serializer(), it.key)
            val value = decode(DummyChannelValue.serializer(), it.value)
            DummySubMessage(key, value, it.scope, it.expiry)
        }.first()

        receivePostgres.pubSub!!.stopSubscribing()

        Assertions.assertEquals(expectedMessage.value, firstMessage.value)
        Assertions.assertEquals(expectedMessage.key, firstMessage.key)
        Assertions.assertEquals(expectedMessage.scope, firstMessage.scope)
        Assertions.assertTrue(expectedMessage.ttl.epochSeconds - firstMessage.ttl.epochSeconds < 1.seconds.inWholeSeconds)
    }
}
