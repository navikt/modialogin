package no.nav.modialogin.common.features.bffproxyfeature.directives

import no.nav.modialogin.common.KotlinUtils.cutoff
import no.nav.modialogin.common.Templating
import no.nav.modialogin.common.features.bffproxyfeature.BFFProxy
import no.nav.modialogin.common.features.bffproxyfeature.RequestDirectiveHandler

object SetHeaderDirectiveSpecification : BFFProxy.RequestDirectiveSpecification {
    /**
     * Usage: SET_HEADER <header name> '<header value>'
     * Ex:
     *  - SET_HEADER Cookie ''
     *  - SET_HEADER Content-Type 'application/json'
     */
    private val regexp = Regex("SET_HEADER (.*?) '(.*?)'")

    private data class Lexed(val header: String, val value: String)

    override fun canHandle(directive: String): Boolean {
        return regexp.matches(directive)
    }

    override fun createHandler(directive: String): RequestDirectiveHandler {
        return { call ->
            val (header, value) = lex(
                Templating.replaceVariableReferences(
                    directive,
                    call.request
                )
            )
            if (value.isBlank()) {
                this.headers.remove(header)
            } else {
                this.headers[header] = value
            }
        }
    }

    override fun describe(directive: String, sb: StringBuilder) {
        val (header, value) = lex(directive)
        sb.appendLine("Set header '$header' to value '${value.cutoff(20)}'")
    }

    private fun lex(directive: String): Lexed {
        val match = requireNotNull(regexp.matchEntire(directive))
        val group = match.groupValues.drop(1)
        return Lexed(group[0], group[1])
    }
}
