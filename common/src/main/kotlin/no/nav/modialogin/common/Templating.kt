package no.nav.modialogin.common

import io.ktor.server.request.*

object Templating {
    private val variablePattern = Regex("\\$(cookie|header|env)\\{(.*?)}")
    fun replaceVariableReferences(str: String, request: ApplicationRequest?): String {
        return variablePattern.replace(str) {
            val groups = it.groupValues
            val match = groups[0]
            val source = groups[1]
            val name = groups[2]

            when (source) {
                "cookie" -> {
                    if (request == null) {
                        "N/A"
                    } else {
                        request.cookies[name] ?: "Cookie not set"
                    }
                }
                "header" -> {
                    if (request == null) {
                        "N/A"
                    } else {
                        request.header(name) ?: "Header not set"
                    }
                }
                "env" -> {
                    KotlinUtils.getProperty(name) ?: "$name not found"
                }
                else -> {
                    throw IllegalStateException("Unknown variable pattern: '$match'")
                }
            }
        }
    }
}
