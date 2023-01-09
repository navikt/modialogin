package no.nav.modialogin.persistence.redis

import kotlinx.serialization.Serializable
import no.nav.modialogin.utils.LocalDateTimeSerializer
import java.time.LocalDateTime

@Serializable
data class RedisEncodedMessage(
    val key: String,
    val value: String,
    @Serializable(LocalDateTimeSerializer::class)
    val expiry: LocalDateTime,
)
