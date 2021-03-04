package no.nav.modialogin

import dev.nohus.autokonfig.*
import dev.nohus.autokonfig.types.BooleanSetting
import dev.nohus.autokonfig.types.StringSetting
import no.nav.modialogin.utils.OidcClient

data class ApplicationState(
    var isAlive: Boolean = true,
    var isReady: Boolean = false
)

class EnvConfig {
    val appname = "modialogin"
    val idpDiscoveryUrl by StringSetting()
    val idpClientId by StringSetting()
    val idpClientSecret by StringSetting()
    val hostStaticFiles by BooleanSetting(default = false)
}

class Config(
    val env: EnvConfig,
    val oidc: OidcClient,
    val state: ApplicationState
) {
    companion object {
        init {
            AutoKonfig
                .clear()
                .withSystemProperties()
                .withEnvironmentVariables()
        }

        fun create(): Config {
            val state = ApplicationState()
            val envConfig = EnvConfig()
            return Config(envConfig, OidcClient(envConfig), state)
        }
    }
}
