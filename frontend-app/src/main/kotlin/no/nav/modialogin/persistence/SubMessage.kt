package no.nav.modialogin.persistence

import java.time.LocalDateTime

data class SubMessage<KEY, VALUE>(
    val scope: String,
    val key: KEY,
    val value: VALUE,
    val ttl: LocalDateTime,
)

