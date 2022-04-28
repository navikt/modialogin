package no.nav.modialogin.common.features

import io.ktor.server.plugins.defaultheaders.*

object CSPFeature {
    fun DefaultHeadersConfig.applyCSPFeature(cspReportOnly: Boolean, cspDirectives: String) {
        if (cspReportOnly) {
            header("Content-Security-Policy-Report-Only", cspDirectives)
        } else {
            header("Content-Security-Policy", cspDirectives)
        }
    }
}
