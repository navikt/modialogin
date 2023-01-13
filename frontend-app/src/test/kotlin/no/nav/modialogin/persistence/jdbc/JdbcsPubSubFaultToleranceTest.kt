package no.nav.modialogin.persistence.jdbc

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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class JdbcsPubSubFaultToleranceTest : TestUtils.WithPostgresFixed {
    @Test()
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    fun `klarer å hente seg inn etter at postgres går ned`() = runBlocking {
        val scope = "test"
        val (sendPostgres, receivePostgres) = TestUtils.setupSendAndReceivePostgres(scope, String.serializer(), DummyChannelValue.serializer(), TestUtils.WithPostgresFixed.container, enablePubSub = true)

        val testKey = "TEST_KEY"
        val testValue = DummyChannelValue("foo", 2, false)
        val ttl = 10.minutes
        val subscription = receivePostgres.pubSub!!.startSubscribing()

        val expectedMessage = DummySubMessage(testKey, testValue, "test", Clock.System.now().plus(10.minutes))

        TestUtils.WithPostgresFixed.stopContainer()
        delay(2000L)
        TestUtils.WithPostgresFixed.startContainer()
        TestUtils.runMigration(TestUtils.postgresHostAndPort(TestUtils.WithPostgresFixed.container))
        delay(2000L)

        sendPostgres.doPut(testKey, testValue, ttl)

        val firstMessage = FlowTransformer.mapData(subscription, EncodedSubMessage.serializer()) {
            val key = Encoding.decode(String.serializer(), it.key)
            val value = Encoding.decode(DummyChannelValue.serializer(), it.value)
            DummySubMessage(key, value, it.scope, it.expiry)
        }.first()

        receivePostgres.pubSub!!.stopSubscribing()

        Assertions.assertEquals(expectedMessage.value, firstMessage.value)
        Assertions.assertEquals(expectedMessage.key, firstMessage.key)
        Assertions.assertEquals(expectedMessage.scope, firstMessage.scope)
        Assertions.assertTrue(expectedMessage.ttl.epochSeconds - firstMessage.ttl.epochSeconds < 1.seconds.inWholeSeconds)
    }
}
