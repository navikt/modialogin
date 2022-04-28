package no.nav.modialogin.common.features

import io.ktor.features.*

object ReferrerPolicyFeature {
    fun DefaultHeaders.Configuration.applyReferrerPolicyFeature(policy: String) {
        header("Referrer-Policy", policy)
    }
}
