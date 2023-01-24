package no.nav.modialogin.persistence.jdbc

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcPubSubTest : PostgresTestUtils.WithPostgres() {

    @Test()
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `mottar postgres-melding på kanal`() = runBlocking {
        container = RestartablePostgresContainer()

        val scope = "test"

        val testUtils = PostgresTestUtils.getIntegrationTestUtils(container, scope, String.serializer(), DummyChannelValue.serializer(), enablePubSub = true)

        val testKey = "TEST_KEY"
        val testValue = DummyChannelValue("foo", 2, false)
        val ttl = 10.minutes
        val subscription = testUtils.receivePostgres.pubSub!!.startSubscribing()

        val expectedMessage = DummySubMessage(testKey, testValue, "test", Clock.System.now().plus(10.minutes))

        testUtils.sendPostgres.doPut(testKey, testValue, ttl)
        val firstMessage = FlowTransformer.mapData(subscription, EncodedSubMessage.serializer()) {
            val key = Encoding.decode(String.serializer(), it.key)
            val value = Encoding.decode(DummyChannelValue.serializer(), it.value)
            DummySubMessage(key, value, it.scope, it.expiry)
        }.first()

        testUtils.receivePostgres.pubSub!!.stopSubscribing()

        Assertions.assertEquals(expectedMessage.value, firstMessage.value)
        Assertions.assertEquals(expectedMessage.key, firstMessage.key)
        Assertions.assertEquals(expectedMessage.scope, firstMessage.scope)
        Assertions.assertTrue(expectedMessage.ttl.epochSeconds - firstMessage.ttl.epochSeconds < 1.seconds.inWholeSeconds)
    }

    @Test()
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    fun `klarer å hente seg inn etter at postgres går ned`() = runBlocking {
        val scope = "test"

        val testUtils = PostgresTestUtils.getIntegrationTestUtils(container, scope, String.serializer(), DummyChannelValue.serializer(), enablePubSub = true)

        val subscription = testUtils.receivePostgres.pubSub!!.startSubscribing(1000L)

        container!!.restart()

        delay(2500L)

        val testKey = "TEST_KEY"
        val testValue = DummyChannelValue("foo", 2, false)
        val ttl = 10.minutes
        val expectedMessage = DummySubMessage(testKey, testValue, "test", Clock.System.now().plus(10.minutes))

        testUtils.sendPostgres.doPut(testKey, testValue, ttl)

        val firstMessage = FlowTransformer.mapData(subscription, EncodedSubMessage.serializer()) {
            val key = Encoding.decode(String.serializer(), it.key)
            val value = Encoding.decode(DummyChannelValue.serializer(), it.value)
            DummySubMessage(key, value, it.scope, it.expiry)
        }.first()

        testUtils.receivePostgres.pubSub!!.stopSubscribing()

        Assertions.assertEquals(expectedMessage.value, firstMessage.value)
        Assertions.assertEquals(expectedMessage.key, firstMessage.key)
        Assertions.assertEquals(expectedMessage.scope, firstMessage.scope)
        Assertions.assertTrue(expectedMessage.ttl.epochSeconds - firstMessage.ttl.epochSeconds < 1.seconds.inWholeSeconds)
    }
}
