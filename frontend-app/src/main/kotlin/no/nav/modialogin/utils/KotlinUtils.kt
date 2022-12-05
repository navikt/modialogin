package no.nav.modialogin.utils

import kotlinx.coroutines.delay
import org.slf4j.MDC
import java.util.*
import kotlin.time.Duration

object KotlinUtils {
    suspend fun <T> retry(numberOfTries: Int, interval: Duration, block: suspend () -> T): T {
        var attempt = 0
        var error: Throwable?
        do {
            try {
                return block()
            } catch (e: Throwable) {
                error = e
            }
            attempt++
            delay(interval)
        } while (attempt < numberOfTries)

        throw error ?: IllegalStateException("Retry failed without error")
    }

    const val callIdProperty = "callId"
    fun callId(): String {
        return MDC.get(callIdProperty) ?: UUID.randomUUID().toString()
    }
}
