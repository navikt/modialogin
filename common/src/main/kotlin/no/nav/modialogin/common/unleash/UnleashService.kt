package no.nav.modialogin.common.unleash

import io.getunleash.DefaultUnleash
import io.getunleash.Unleash
import io.getunleash.UnleashContext
import io.getunleash.util.UnleashConfig

class UnleashService(
    appname: String,
    unleashApiUrl: String,
) {
    private val unleashConfig = UnleashConfig
        .builder()
        .appName(appname)
        .unleashAPI(unleashApiUrl)
        .build()

    private val unleash: Unleash = DefaultUnleash(unleashConfig)
    fun isEnabled(name: String, context: UnleashContext): Boolean {
        return unleash.isEnabled(name, context)
    }

}