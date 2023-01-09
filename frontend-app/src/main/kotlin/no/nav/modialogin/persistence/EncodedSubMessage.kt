package no.nav.modialogin.persistence

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class EncodedSubMessage(
    val scope: String,
    val key: String,
    val value: String,
    @SerialName("expiry")
    private val _expiry: LocalDateTime,
) {
    @Transient
    val expiry = _expiry.toInstant(TimeZone.currentSystemDefault())
}
