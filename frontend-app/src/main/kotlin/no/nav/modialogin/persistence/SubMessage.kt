package no.nav.modialogin.persistence

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class SubMessage<KEY, VALUE>(
    val key: KEY,
    val value: VALUE,
    val ttl: Instant,
)

