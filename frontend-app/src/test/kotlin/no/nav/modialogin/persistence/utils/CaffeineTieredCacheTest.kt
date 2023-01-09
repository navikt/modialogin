package no.nav.modialogin.persistence.utils

import com.github.benmanes.caffeine.cache.Expiry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import no.nav.modialogin.persistence.DummyChannelValue
import no.nav.modialogin.persistence.Persistence
import no.nav.modialogin.persistence.TestUtils
import no.nav.modialogin.persistence.TestUtils.setupSendAndReceiveRedis
import no.nav.modialogin.utils.CaffeineTieredCache
import no.nav.personoversikt.common.utils.SelftestGenerator
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CaffeineTieredCacheTest : TestUtils.WithRedis {
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
        keySerializer = String.serializer(),
        valueSerializer = DummyChannelValue.serializer(),
    )

    @Test()
    fun `lagrer pub-sub verdi i egen cache`() = runBlocking {
        val (sendRedis, receiveRedis) = setupSendAndReceiveRedis(
            "test",
            String.serializer(),
            DummyChannelValue.serializer(),
            enablePubSub = true
        )

        val testKey = "TEST_KEY"
        val testValue = DummyChannelValue("foo", 2, false)
        val ttl = 10.seconds

        val caffeineTieredCache = getCaffeineTieredCache(receiveRedis)

        sendRedis.doPut(testKey, testValue, ttl)
        delay(1000L)
        val entryInCache = caffeineTieredCache.get(testKey)
        Assertions.assertNotNull(entryInCache)
    }

    @Test()
    fun `lagrer pub-sub verdi i egen cache om pub-sub verdi er gyldig lenger`() = runBlocking {
        val (sendRedis, receiveRedis) = setupSendAndReceiveRedis(
            "test",
            String.serializer(),
            DummyChannelValue.serializer(),
            enablePubSub = true
        )

        val testKey = "TEST_KEY"

        val caffeineTieredCache = getCaffeineTieredCache(receiveRedis)

        val valueToPersist = DummyChannelValue("store me", 2, false)
        val valueNotToPersist = DummyChannelValue("do not store me", 2, false)

        caffeineTieredCache.put(testKey, valueNotToPersist)

        sendRedis.doPut(testKey, valueToPersist, 1.hours)

        delay(1000L)

        val entryInCache = caffeineTieredCache.get(testKey)

        Assertions.assertEquals(valueToPersist, entryInCache)
    }

    @Test()
    fun `lagrer ikke pub-sub verdi i egen cache om egen verdi er gyldig lenger`() = runBlocking {
        val (sendRedis, receiveRedis) = setupSendAndReceiveRedis(
            "test",
            String.serializer(),
            DummyChannelValue.serializer(),
            enablePubSub = true
        )

        val testKey = "TEST_KEY"

        val caffeineTieredCache = getCaffeineTieredCache(receiveRedis)

        val valueNotToPersist = DummyChannelValue("do not store me", 2, false)

        val valueToPersist = DummyChannelValue("store me", 2, false)

        caffeineTieredCache.put(testKey, valueNotToPersist)

        sendRedis.doPut(testKey, valueToPersist, 15.minutes)

        delay(1000L)

        val entryInCache = caffeineTieredCache.get(testKey)

        Assertions.assertEquals(valueToPersist, entryInCache)
    }
}
