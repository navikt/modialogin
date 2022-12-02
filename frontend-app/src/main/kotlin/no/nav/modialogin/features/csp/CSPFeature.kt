package no.nav.modialogin.features.csp

import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.response.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import no.nav.modialogin.common.TemplatingEngine

object CSPFeature {
    private val cspRequestNonceKey = AttributeKey<CSPNonceHolder>("templating.feature.csp.requested.nonces")

    class Config(
        var reportOnly: Boolean = false,
        var directive: String = "default-src 'self';",
    )

    val Plugin = createRouteScopedPlugin("csp-headers", CSPFeature::Config) {
        val reportOnly = pluginConfig.reportOnly
        val directives = CSPDirective(pluginConfig.directive)
        on(CallSetup) { call ->
            val nonceholder = CSPNonceHolder()
            call.attributes.put(cspRequestNonceKey, nonceholder)
        }

        on(ResponseBodyReadyForSend) { call, _ ->
            val requestDirectives = directives.copy()
            call.attributes[cspRequestNonceKey]
                .nonces
                .entries
                .forEach { (key, nonce) ->
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

    class CSPNonceHolder {
        internal val nonces: MutableMap<String, String> = mutableMapOf()

        fun generateNonce(name: String): String = nonces.getOrPut(name) {
            runBlocking {
                GenerateOnlyNonceManager.newNonce()
            }
        }
    }

    val NonceSource = TemplatingEngine.Source<ApplicationCall?>(
        prefix = "csp",
        replacement = { call, name ->
            if (call == null) return@Source "N/A"
            call.attributes.getOrNull(cspRequestNonceKey)?.generateNonce(name)?: "Unknown"
        }
    )
}
