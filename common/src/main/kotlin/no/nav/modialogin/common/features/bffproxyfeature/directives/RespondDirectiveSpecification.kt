package no.nav.modialogin.common.features.bffproxyfeature.directives

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import no.nav.modialogin.common.KotlinUtils.cutoff
import no.nav.modialogin.common.Templating
import no.nav.modialogin.common.features.bffproxyfeature.BFFProxy
import no.nav.modialogin.common.features.bffproxyfeature.ResponseDirectiveHandler

object RespondDirectiveSpecification : BFFProxy.ResponseDirectiveSpecification {
    private val regexp = Regex("RESPOND (.*?) '(.*?)'")
    private data class Lexed(val code: HttpStatusCode, val body: String)
    override fun canHandle(directive: String): Boolean {
        return regexp.matches(directive)
    }

    override fun createHandler(directive: String): ResponseDirectiveHandler {
        return {
            val (code, body) = lex(Templating.replaceVariableReferences(directive, this.call.request))
            this.call.response.status(code)
            this.call.respond(body)
        }
    }

    override fun describe(directive: String, sb: StringBuilder) {
        val (code, body) = lex(directive)
        sb.appendLine("Respond with code $code and body: '${body.cutoff(20)}'")
    }

    private fun lex(directive: String): Lexed {
        val match = requireNotNull(regexp.matchEntire(directive))
        val group = match.groupValues.drop(1)
        return Lexed(HttpStatusCode.fromValue(group[0].toInt()), group[1])
    }
}
