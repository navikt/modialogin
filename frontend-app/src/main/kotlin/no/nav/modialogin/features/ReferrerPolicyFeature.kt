package no.nav.modialogin.features

import io.ktor.server.plugins.defaultheaders.*

object ReferrerPolicyFeature {
    fun DefaultHeadersConfig.applyReferrerPolicyFeature(policy: String) {
        header("Referrer-Policy", policy)
    }
}
