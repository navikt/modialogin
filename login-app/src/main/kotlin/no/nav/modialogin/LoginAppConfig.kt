package no.nav.modialogin

import dev.nohus.autokonfig.types.BooleanSetting
import dev.nohus.autokonfig.types.IntSetting
import dev.nohus.autokonfig.types.StringSetting

class LoginAppConfig {
    val appname = "modialogin"
    val idpDiscoveryUrl by StringSetting()
    val idpClientId by StringSetting()
    val idpClientSecret by StringSetting()
    val xForwardingPort by IntSetting(default = 8080)
    val dockerCompose by BooleanSetting(default = false)
}
