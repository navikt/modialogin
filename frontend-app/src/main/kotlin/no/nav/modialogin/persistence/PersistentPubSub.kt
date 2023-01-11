package no.nav.modialogin.persistence

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import java.time.LocalDateTime

abstract class PersistentPubSub<KEY, VALUE>(
    open val channelName: String,
    val keySerializer: KSerializer<KEY>,
    val valueSerializer: KSerializer<VALUE>
) {
    abstract fun startSubscribing(): Flow<SubMessage<KEY, VALUE>>
    abstract fun stopSubscribing()
    abstract suspend fun publishMessage(scope: String, key: String, value: String, expiry: LocalDateTime)
}
