package no.nav.modialogin.persistence

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class DummySubMessage<KEY, VALUE>(
    val key: KEY,
    val value: VALUE,
    val scope: String,
    val ttl: Instant,
)
