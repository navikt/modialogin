package no.nav.modialogin.persistence

import kotlinx.serialization.Serializable
import no.nav.modialogin.utils.LocalDateTimeSerializer
import java.time.LocalDateTime

@Serializable
data class EncodedSubMessage(
    val scope: String,
    val key: String,
    val value: String,
    @Serializable(LocalDateTimeSerializer::class)
    val expiry: LocalDateTime,
)
