package no.nav.modialogin.persistence

import kotlinx.coroutines.flow.Flow

abstract class PersistencePubSub(
    open val channelName: String,
) {
    abstract fun startSubscribing(): Flow<String>
    abstract fun stopSubscribing()
    abstract suspend fun publishData(data: String)
}
