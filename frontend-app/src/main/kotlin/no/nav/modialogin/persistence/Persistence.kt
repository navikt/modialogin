package no.nav.modialogin.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration

abstract class Persistence<KEY, VALUE>(val scope: String, val pubSub: PersistentPubSub? = null) {
    suspend fun get(key: KEY): VALUE? = withContext(Dispatchers.IO) { doGet(key) }
    suspend fun put(key: KEY, value: VALUE, ttl: Duration) = withContext(Dispatchers.IO) { doPut(key, value, ttl) }
    suspend fun remove(key: KEY) = withContext(Dispatchers.IO) { doRemove(key) }
    suspend fun clean() = withContext(Dispatchers.IO) { doClean() }
    suspend fun dump(): Map<KEY, VALUE> = withContext(Dispatchers.IO) { doDump() }
    abstract suspend fun doGet(key: KEY): VALUE?
    abstract suspend fun doPut(key: KEY, value: VALUE, ttl: Duration)
    abstract suspend fun doRemove(key: KEY)
    abstract suspend fun doClean()
    abstract suspend fun doDump(): Map<KEY, VALUE>
}
