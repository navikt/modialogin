package no.nav.modialogin.utils

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import com.github.benmanes.caffeine.cache.Ticker
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import no.nav.modialogin.Logging.log
import no.nav.modialogin.persistence.EncodedSubMessage
import no.nav.modialogin.persistence.Persistence
import no.nav.modialogin.persistence.SubMessage
import no.nav.personoversikt.common.utils.SelftestGenerator
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.time.toJavaDuration

class CaffeineTieredCache<KEY, VALUE>(
    val expirationStrategy: Expiry<KEY, VALUE>,
    val persistence: Persistence<KEY, VALUE>,
    val selftest: SelftestGenerator.Reporter,
) {
    private val ticker = Ticker.systemTicker()
    private val localCache = Caffeine
        .newBuilder()
        .expireAfter(expirationStrategy)
        .build<KEY, VALUE>()
    private var channelConsumerJob: Job? = null

    init {
        runBlocking {
            log.info("Loading cached data from persistence backup for ${selftest.name}")
            try {
                val persistedValues = persistence.dump()
                localCache.putAll(persistedValues)
                selftest.reportOk()
                log.info("Cached data loaded for ${selftest.name}: ${persistedValues.size}")
            } catch (e: Throwable) {
                log.error("Failed to load cached data for ${selftest.name}")
                selftest.reportError(e)
            }
        }
        if (persistence.pubSub != null) {
            subscribeToPersistentUpdates()
        }
        SelftestGenerator.Metadata(name = "${selftest.name} in-memory size") {
            localCache.estimatedSize().toString()
        }
        SelftestGenerator.Metadata(name = "${selftest.name} persistent size") {
            runBlocking {
                persistence.size().toString()
            }
        }
    }

    suspend fun get(key: KEY): VALUE? {
        return localCache.get(key) {
            runBlocking {
                persistence.get(key)
            }
        }
    }

    suspend fun put(key: KEY, value: VALUE) {
        val expiry = expirationStrategy.expireAfterCreate(key, value, ticker.read()).nanoseconds
        localCache.put(key, value)
        persistence.put(key, value, expiry)
    }

    suspend fun invalidate(key: KEY) {
        persistence.remove(key)
        localCache.invalidate(key)
    }
    private fun subscribeToPersistentUpdates() {
        channelConsumerJob = GlobalScope.launch {
            doSubscribeToPersistentUpdates()
        }
    }

    private suspend fun doSubscribeToPersistentUpdates() {
        if (persistence.pubSub == null) return

        val subscription = persistence.pubSub.startSubscribing()

        subscription.map {
            val decodedMessage = Encoding.decode(EncodedSubMessage.serializer(), it)
            val key = persistence.decodeKey(decodedMessage.key)
            val value = persistence.decodeValue(decodedMessage.value)
            SubMessage(key, value, decodedMessage.expiry)
        }.collect { (key, value, expiry) ->
            val ttl = expiry.epochSeconds - Clock.System.now().epochSeconds
            if (checkIfNewValueFromPubSubShouldBeStored(key, ttl)) {
                localCache.policy().expireVariably().get().put(key, value, ttl.toDuration(DurationUnit.SECONDS).toJavaDuration())
            }
        }
    }

    private fun checkIfNewValueFromPubSubShouldBeStored(key: KEY, ttl: Long): Boolean {
        localCache.getIfPresent(key) ?: return true
        val existingTtl = localCache.policy().expireVariably().get().getExpiresAfter(key) ?: return true
        return ttl > existingTtl.get().seconds
    }

    fun stop() {
        if (persistence.pubSub == null) return
        persistence.pubSub.stopSubscribing()
        channelConsumerJob?.cancel()
    }
}
