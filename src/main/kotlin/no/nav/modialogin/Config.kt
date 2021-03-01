package no.nav.modialogin

import dev.nohus.autokonfig.*
import dev.nohus.autokonfig.types.BooleanSetting
import dev.nohus.autokonfig.types.StringSetting
import no.nav.modialogin.utils.OidcClient
import no.nav.modialogin.utils.OidcClientImpl
import no.nav.modialogin.utils.OidcClientMock

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
        fun create(useMock: Boolean): Config {
            val state = ApplicationState()
            return when (useMock) {
                true -> setupMock(state)
                false -> setupConfig(state)
            }
        }

        private fun setupConfig(state: ApplicationState): Config {
            AutoKonfig
                .clear()
                .withSystemProperties()
                .withEnvironmentVariables()

            val envConfig = EnvConfig()
            return Config(envConfig, OidcClientImpl(envConfig), state)
        }

        private fun setupMock(state: ApplicationState): Config {
            AutoKonfig
                .clear()
                .withSystemProperties()
                .withMap(
                    mapOf(
                        "HOST_STATIC_FILES" to "false", // Optional
                        "IDP_DISCOVERY_URL" to "http://localhost:8081/.well-known/openid-configuration",
                        "IDP_CLIENT_ID" to "modia",
                        "IDP_CLIENT_SECRET" to "secret here"
                    )
                )

            val envConfig = EnvConfig()
            return Config(envConfig, OidcClientMock(), state)
        }
    }
}
