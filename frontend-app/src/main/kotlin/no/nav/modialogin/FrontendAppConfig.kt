package no.nav.modialogin

import dev.nohus.autokonfig.types.BooleanSetting
import dev.nohus.autokonfig.types.IntSetting
import dev.nohus.autokonfig.types.StringSetting

class FrontendAppConfig {
    val appname by StringSetting()
    val idpDiscoveryUrl by StringSetting()
    val idpClientId by StringSetting()
    val delegatedLoginUrl by StringSetting()
    val xForwardingPort by IntSetting(default = 8080)
    val dockerCompose by BooleanSetting(default = false)
}
