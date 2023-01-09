package no.nav.modialogin.persistence.utils

import com.github.benmanes.caffeine.cache.Expiry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import no.nav.modialogin.persistence.*
import no.nav.modialogin.utils.CaffeineTieredCache
import no.nav.personoversikt.common.utils.SelftestGenerator
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CaffeineTieredCachePubSubTest : RedisTestUtils.WithRedis() {

    private val expirationStrategy = object : Expiry<String, DummyChannelValue> {
        override fun expireAfterCreate(key: String, value: DummyChannelValue, currentTime: Long): Long {
            return 10.minutes.inWholeNanoseconds
        }

        override fun expireAfterUpdate(
            key: String,
            value: DummyChannelValue,
            currentTime: Long,
            currentDuration: Long
        ): Long {
            return currentDuration
        }

        override fun expireAfterRead(key: String, value: DummyChannelValue, currentTime: Long, currentDuration: Long): Long {
            return currentDuration
        }
    }

    private fun getCaffeineTieredCache(persistence: Persistence<String, DummyChannelValue>) = CaffeineTieredCache(
        persistence = persistence,
        expirationStrategy = expirationStrategy,
        selftest = SelftestGenerator.Reporter("tokencache", false),
    )

    @Test()
    fun `lagrer pub-sub verdi i egen cache`() = runBlocking {
        val testUtils = RedisTestUtils.getIntegrationTestUtils(
            container,
            "test",
            String.serializer(),
            DummyChannelValue.serializer(),
            enablePubSub = true
        )

        val testKey = "TEST_KEY"
        val testValue = DummyChannelValue("foo", 2, false)
        val ttl = 10.seconds

        val caffeineTieredCache = getCaffeineTieredCache(testUtils.receiveRedis)

        testUtils.sendRedis.doPut(testKey, testValue, ttl)
        delay(2000L)

        val entryInCache = caffeineTieredCache.get(testKey)
        Assertions.assertNotNull(entryInCache)
    }

    @Test()
    fun `lagrer pub-sub verdi i egen cache om pub-sub verdi er gyldig lenger`() = runBlocking {
        val testUtils = RedisTestUtils.getIntegrationTestUtils(
            container,
            "test",
            String.serializer(),
            DummyChannelValue.serializer(),
            enablePubSub = true
        )

        val caffeineTieredCache = getCaffeineTieredCache(testUtils.receiveRedis)

        val testKey = "TEST_KEY"
        val valueToPersist = DummyChannelValue("store me", 2, false)
        val valueNotToPersist = DummyChannelValue("do not store me", 2, false)

        caffeineTieredCache.put(testKey, valueNotToPersist)
        delay(2000L)
        testUtils.sendRedis.doPut(testKey, valueToPersist, 1.hours)
        delay(2000L)

        val entryInCache = caffeineTieredCache.get(testKey)
        Assertions.assertEquals(valueToPersist, entryInCache)
    }

    @Test()
    fun `lagrer ikke pub-sub verdi i egen cache om egen verdi er gyldig lenger`() = runBlocking {
        val testUtils = RedisTestUtils.getIntegrationTestUtils(
            container,
            "test",
            String.serializer(),
            DummyChannelValue.serializer(),
            enablePubSub = true
        )

        val caffeineTieredCache = getCaffeineTieredCache(testUtils.receiveRedis)

        val testKey = "TEST_KEY"
        val valueNotToPersist = DummyChannelValue("do not store me", 2, false)
        val valueToPersist = DummyChannelValue("store me", 2, false)

        caffeineTieredCache.put(testKey, valueToPersist)
        delay(2000L)
        testUtils.sendRedis.doPut(testKey, valueNotToPersist, 5.minutes)
        delay(2000L)


        val entryInCache = caffeineTieredCache.get(testKey)
        Assertions.assertEquals(valueToPersist, entryInCache)
    }
}
