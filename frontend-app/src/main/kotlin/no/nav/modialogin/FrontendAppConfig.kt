package no.nav.modialogin

import dev.nohus.autokonfig.Value
import dev.nohus.autokonfig.types.*
import no.nav.modialogin.common.features.bffproxyfeature.BFFProxyFeature.ProxyConfig

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

    private val domain = if (dockerCompose) "echo-server" else "localhost:8089"
    val proxyConfig by ListSetting(ProxyConfigType, default = listOf(
        ProxyConfig(
            prefix = "api",
            url = "http://$domain/modiapersonoversikt-api"
        ),
        ProxyConfig(
            prefix = "proxy/app1",
            url = "http://$domain/appname1"
        ),
        ProxyConfig(
            prefix = "proxy/app2",
            url = "http://$domain/appname2"
        ),
        ProxyConfig(
            prefix = "proxy/open-endpoint",
            url = "http://$domain"
        ),
        ProxyConfig(
            prefix = "proxy/open-endpoint-no-cookie",
            url = "http://$domain",
            rewriteDirectives = listOf(
                "SET_HEADER Cookie ''"
            )
        ),
        ProxyConfig(
            prefix = "proxy/protected-endpoint",
            url = "http://$domain"
        ),
        ProxyConfig(
            prefix = "proxy/protected-endpoint-with-cookie-rewrite",
            url = "http://$domain",
            rewriteDirectives = listOf(
                "SET_HEADER Cookie 'ID_token=\$cookie{loginapp_ID_token}'",
                "SET_HEADER Authorization '\$cookie{loginapp_ID_token}'",
            )
        ),
        ProxyConfig(
            prefix = "env-data",
            rewriteDirectives = listOf(
                "RESPOND 200 'APP_NAME: \$env{APP_NAME}'"
            )
        )
    ))
}

val ProxyConfigType: SettingType<ProxyConfig> = SettingType(::mapProxyConfig)
private fun mapProxyConfig(value: Value): ProxyConfig {
    TODO("Not yet implemented")
}