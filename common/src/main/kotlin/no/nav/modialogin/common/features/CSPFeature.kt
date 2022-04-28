package no.nav.modialogin.common.features

import io.ktor.features.*

object CSPFeature {
    fun DefaultHeaders.Configuration.applyCSPFeature(cspReportOnly: Boolean, cspDirectives: String) {
        if (cspReportOnly) {
            header("Content-Security-Policy-Report-Only", cspDirectives)
        } else {
            header("Content-Security-Policy", cspDirectives)
        }
    }
}
