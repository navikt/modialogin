package no.nav.modialogin.utils

import io.ktor.server.application.*
import io.ktor.server.request.*

class TemplatingEngine<CTX>(vararg sources: Source<CTX>) {
    class Source<CTX>(val prefix: String, val replacement: (CTX, String) -> String)

    private val variablePattern = Regex("\\$(${sources.joinToString("|") { it.prefix }})\\{(.*?)}")
    private val sourceLUT = sources.associateBy { it.prefix }

    fun replaceVariableReferences(ctx: CTX, str: String): String {
        return variablePattern.replace(str) {
            val groups = it.groupValues
            val match = groups[0]
            val sourceKey = groups[1]
            val name = groups[2]

            val source = sourceLUT[sourceKey] ?: error("Unknown variable pattern: '$match'")
            source.replacement(ctx, name)
        }
    }
}

fun interface RequestTemplating {
    fun replaceVariableReferences(str: String, request: ApplicationCall?): String
}

object Templating : RequestTemplating {
    val CookieSource = TemplatingEngine.Source<ApplicationCall?>(
        prefix = "cookie",
        replacement = { call, name ->
            if (call == null) {
                "N/A"
            } else {
                call.request.cookies[name] ?: "Cookie not set"
            }
        }
    )
    val HeaderSource = TemplatingEngine.Source<ApplicationCall?>(
        prefix = "header",
        replacement = { call, name ->
            if (call == null) {
                "N/A"
            } else {
                call.request.header(name) ?: "Header not set"
            }
        }
    )
    val EnvSource = TemplatingEngine.Source<ApplicationCall?>(
        prefix = "env",
        replacement = { _, name -> KotlinUtils.getProperty(name) ?: "$name not found" }
    )

    private val standard = TemplatingEngine(
        CookieSource,
        HeaderSource,
        EnvSource,
    )

    override fun replaceVariableReferences(str: String, call: ApplicationCall?): String {
        return standard.replaceVariableReferences(call, str)
    }
}