package no.nav.modialogin.common.unleash

import io.getunleash.FakeUnleash
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
    private val unleash: Unleash = FakeUnleash().apply {
        enable("feature1")
        disable("feature2")
        enable("feature3")
    }

    fun isEnabled(name: String, context: UnleashContext): Boolean {
        return unleash.isEnabled(name, context)
    }

}