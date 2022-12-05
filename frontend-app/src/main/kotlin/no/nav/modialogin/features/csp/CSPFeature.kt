package no.nav.modialogin.features.csp

import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.response.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import no.nav.modialogin.utils.TemplatingEngine


class CSPFeatureConfig(
    var reportOnly: Boolean = false,
    var directive: String = "default-src 'self';",
)

val CSPFeature = createApplicationPlugin("csp-headers", ::CSPFeatureConfig) {
    val reportOnly = pluginConfig.reportOnly
    val directives = CSPDirective(pluginConfig.directive)
    on(CallSetup) { call ->
        val nonceholder = CSPNonceHolder()
        call.attributes.put(nonceholderKey, nonceholder)
    }

    on(ResponseBodyReadyForSend) { call, _ ->
        val requestDirectives = directives.copy()
        call.attributes[nonceholderKey].nonces.entries.forEach { (key, nonce) ->
                val (type, _) = key.split(":", limit = 2).map { it.trim() }
                val directive = CSPDirective.Directive.reverseLUT.getOrElse(type) {
                    error("Could not find directive of type $type")
                }
                requestDirectives.add(directive, "'nonce-$nonce'")
            }

        if (reportOnly) {
            call.response.header("Content-Security-Policy-Report-Only", requestDirectives.toString())
        } else {
            call.response.header("Content-Security-Policy", requestDirectives.toString())
        }
    }
}

val CSPNonceSource = TemplatingEngine.Source<ApplicationCall?>(prefix = "csp", replacement = { call, name ->
    if (call == null) return@Source "N/A"
    call.attributes.getOrNull(nonceholderKey)?.generateNonce(name) ?: "Unknown"
})

private val nonceholderKey = AttributeKey<CSPNonceHolder>("templating.feature.csp.requested.nonces")
private class CSPNonceHolder {
    internal val nonces: MutableMap<String, String> = mutableMapOf()

    fun generateNonce(name: String): String = nonces.getOrPut(name) {
        runBlocking {
            GenerateOnlyNonceManager.newNonce()
        }
    }
}