package no.nav.modialogin.common

import dev.nohus.autokonfig.AutoKonfig
import dev.nohus.autokonfig.clear
import dev.nohus.autokonfig.withEnvironmentVariables
import dev.nohus.autokonfig.withSystemProperties

data class NaisState(
    var isAlive: Boolean = true,
    var isReady: Boolean = false
)

data class AppState<T>(
    val nais: NaisState,
    val config: T
) {
    companion object {
        init {
            AutoKonfig
                .clear()
                .withSystemProperties()
                .withEnvironmentVariables()
        }

        fun <T> create(naisState: NaisState, fn: () -> T): AppState<T> {
            val config: T = fn()
            return AppState(naisState, config)
        }
    }
}
