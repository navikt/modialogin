package no.nav.modialogin.utils

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import com.github.benmanes.caffeine.cache.Ticker
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import no.nav.modialogin.Logging.log
import no.nav.modialogin.persistence.Persistence
import no.nav.modialogin.utils.Encoding.decode
import no.nav.personoversikt.common.utils.SelftestGenerator
import java.time.Duration
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.nanoseconds

class CaffeineTieredCache<KEY, VALUE>(
    val expirationStrategy: Expiry<KEY, VALUE>,
    val persistence: Persistence<KEY, VALUE>,
    val selftest: SelftestGenerator.Reporter,
    private val keySerializer: KSerializer<KEY>,
    private val valueSerializer: KSerializer<VALUE>
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
        persistence.pubSub.startSubscribing().filterNotNull().collect { (encodedKey, encodedValue, expiry) ->
            val key = decode(keySerializer, encodedKey)
            val value = decode(valueSerializer, encodedValue)
            val ttl = Duration.between(LocalDateTime.now(), expiry)
            if (checkIfNewValueFromPubSubShouldBeStored(key, ttl)) {
                localCache.policy().expireVariably().get().put(key, value, ttl)
            }
        }
    }

    private fun checkIfNewValueFromPubSubShouldBeStored(key: KEY, ttl: Duration): Boolean {
        localCache.getIfPresent(key) ?: return true
        val existingTtl = localCache.policy().expireVariably().get().getExpiresAfter(key) ?: return true
        return ttl > existingTtl.get()
    }

    // TODO: Do we need this?
    fun stop() {
        if (persistence.pubSub == null) return
        persistence.pubSub.stopSubscribing()
        channelConsumerJob?.cancel()
    }
}
