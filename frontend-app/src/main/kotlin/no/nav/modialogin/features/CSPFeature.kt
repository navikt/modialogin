package no.nav.modialogin.features

import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.response.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import no.nav.modialogin.common.TemplatingEngine

object CSPFeature {
    private val cspRequestNonceKey = AttributeKey<CSPNonceHolder>("templating.feature.csp.requested.nonces")
    class CSPNonceHolder {
        internal val nonces: MutableMap<String, String> = mutableMapOf()
        fun generateNonce(name: String): String = nonces.getOrPut(name) {
            runBlocking {
                GenerateOnlyNonceManager.newNonce()
            }
        }
    }
    fun DefaultHeadersConfig.applyCSPFeature(cspReportOnly: Boolean, cspDirectives: String) {
        if (cspReportOnly) {
            header("Content-Security-Policy-Report-Only", cspDirectives)
        } else {
            header("Content-Security-Policy", cspDirectives)
        }
    }
    class Config {
        var reportOnly: Boolean = false
        var directive: String = "default-src 'self';"
    }
    val Plugin = createRouteScopedPlugin("csp-headers", ::Config) {
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
                    val (type, value) = key.split(":", limit = 2).map { it.trim() }
                    val directive = reverseLUTDirective.getOrElse(type) {
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

    val NonceSource = TemplatingEngine.Source<ApplicationCall?>(
        prefix = "csp",
        replacement = { call, name ->
            if (call == null) return@Source "N/A"
            call.attributes.getOrNull(cspRequestNonceKey)?.generateNonce(name)?: "Unknown"
        }
    )

    private fun parseCSPDirective(directive: String):List<String> {
        return directive.split(";").map { it.trim() }.filter { it.isNotBlank() }
    }

    class CSPDirective(private val directives: MutableMap<Directive, MutableList<String>>) {
        override fun toString(): String = directives
            .filter { it.value.isNotEmpty() }
            .map {
                val directive = arrayOf(it.key.directive, *it.value.toTypedArray())
                directive.joinToString(" ")
            }.joinToString(";")

        fun copy(): CSPDirective = CSPDirective(
            directives.entries.associateTo(LinkedHashMap()) { it.key to it.value.toMutableList() }
        )
        fun add(directive: Directive, part: String) {
            directives.getOrPut(directive) { mutableListOf() }.add(part)
        }
        companion object {
            operator fun invoke(value: String): CSPDirective {
                val directives = value.split(";")
                    .filter { it.isNotBlank() }
                    .associateTo(LinkedHashMap()) { directive ->
                        val words = directive.split(" ").filter { it.isNotBlank() }
                        val type = requireNotNull(reverseLUTDirective[words.first()])
                        val directiveValue = words.drop(1).toMutableList()
                        type to directiveValue
                    }
                return CSPDirective(directives)
            }
        }
    }
    private val reverseLUTDirective: Map<String, Directive> = Directive.values().associateBy {
        it.directive
    }

    enum class Directive(val directive: String) {
        DEFAULT_SRC("default-src"),
        CHILD_SRC("child-src"),
        CONNECT_SRC("connect-src"),
        FONT_SRC("font-src"),
        FRAME_SRC("frame-src"),
        IMG_SRC("img-src"),
        MANIFEST_SRC("manifest-src"),
        MEDIA_SRC("media-src"),
        OBJECT_SRC("object-src"),
        PREFETCH_SRC("prefetch-src"),
        SCRIPT_SRC("script-src"),
        STYLE_SRC("style-src"),
        WORKER_SRC("worker-src"),
    }
}
