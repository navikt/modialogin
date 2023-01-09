package no.nav.modialogin.persistence

import kotlinx.serialization.Serializable

@Serializable
data class DummyChannelValue(
    val foo: String,
    val bar: Int,
    val baz: Boolean
)
