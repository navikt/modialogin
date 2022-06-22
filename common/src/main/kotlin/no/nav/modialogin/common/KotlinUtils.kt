package no.nav.modialogin.common

import kotlinx.coroutines.delay
import kotlin.time.Duration

object KotlinUtils {
    fun String.cutoff(n: Int): String {
        return if (this.length <= n) {
            this
        } else {
            this.substring(0, n - 3) + "..."
        }
    }

    fun getProperty(name: String): String? = System.getProperty(name, System.getenv(name))
    fun requireProperty(name: String): String = requireNotNull(getProperty(name)) {
        "'$name' was not defined"
    }

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

    fun CharSequence.indicesOf(other: String, startIndex: Int = 0, ignoreCase: Boolean = false): List<Int> {
        val indices = mutableListOf<Int>()
        var index = this.indexOf(other, startIndex, ignoreCase)
        while (index != -1) {
            indices.add(index)
            index = this.indexOf(other, index + 1, ignoreCase)
        }
        return indices
    }
}
