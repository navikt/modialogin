package no.nav.modialogin.persistence

import java.time.LocalDateTime

data class SubMessage(
    val key: String,
    val value: String,
    val ttl: LocalDateTime
)

