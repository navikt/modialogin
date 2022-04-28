package no.nav.modialogin

import dev.nohus.autokonfig.types.BooleanSetting
import dev.nohus.autokonfig.types.IntSetting
import dev.nohus.autokonfig.types.StringSetting

class FrontendAppConfig {
    val appName by StringSetting()
    val appVersion by StringSetting()
    val idpDiscoveryUrl by StringSetting()
    val idpClientId by StringSetting()
    val delegatedLoginUrl by StringSetting()
    val authTokenResolver by StringSetting(default = "ID_token")
    val cspReportOnly by BooleanSetting(default = false)
    val cspDirectives by StringSetting(default = "default src 'self'")
    val referrerPolicy by StringSetting(default = "origin")
    val exposedPort by IntSetting(default = 8080)
    val dockerCompose by BooleanSetting(default = false)
}
