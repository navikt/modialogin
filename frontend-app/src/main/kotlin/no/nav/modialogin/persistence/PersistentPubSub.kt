package no.nav.modialogin.persistence

import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

abstract class PersistentPubSub(
    val channelName: String,
) {
    abstract fun startSubscribing(): Flow<SubMessage>
    abstract fun stopSubscribing()
    abstract suspend fun publishMessage(key: String, value: String, expiry: LocalDateTime)
}
