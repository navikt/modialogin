package no.nav.modialogin

import dev.nohus.autokonfig.types.BooleanSetting
import dev.nohus.autokonfig.types.IntSetting
import dev.nohus.autokonfig.types.StringSetting

class LoginAppConfig {
    val appName by StringSetting()
    val appVersion by StringSetting()
    val idpDiscoveryUrl by StringSetting()
    val idpClientId by StringSetting()
    val idpClientSecret by StringSetting()
    val authTokenResolver by StringSetting(default = "ID_token")
    val refreshTokenResolver by StringSetting(default = "refresh_token")
    val xForwardingPort by IntSetting(default = 8080)
    val dockerCompose by BooleanSetting(default = false)
}
