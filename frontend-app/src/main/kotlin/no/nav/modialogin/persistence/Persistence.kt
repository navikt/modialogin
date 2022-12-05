package no.nav.modialogin.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration

abstract class Persistence<KEY, VALUE>(val scope: String) {
    suspend fun get(key: KEY): VALUE? = withContext(Dispatchers.IO) { doGet(key) }
    suspend fun put(key: KEY, value: VALUE, expiry: Duration?) = withContext(Dispatchers.IO) { doPut(key, value, expiry) }
    suspend fun remove(key: KEY) = withContext(Dispatchers.IO) { doRemove(key) }
    abstract suspend fun doGet(key: KEY): VALUE?
    abstract suspend fun doPut(key: KEY, value: VALUE, expiry: Duration?)
    abstract suspend fun doRemove(key: KEY)
}