package no.nav.modialogin.features.bffproxyfeature

import io.ktor.client.request.*
import io.ktor.server.application.*
import io.ktor.util.pipeline.*
import java.lang.StringBuilder

typealias ResponseDirectiveHandler = suspend PipelineContext<Unit, ApplicationCall>.() -> Unit
typealias RequestDirectiveHandler = HttpRequestBuilder.(ApplicationCall) -> Unit
class BFFProxy(private val handlers: List<DirectiveSpecification>) {
    constructor(vararg handlers: DirectiveSpecification): this(handlers.toList())

    enum class DirectiveSpecificationType {
        RESPONSE, REQUEST
    }
    sealed interface DirectiveSpecification {
        fun canHandle(directive: String): Boolean
        fun describe(directive: String, sb: StringBuilder)
        val type: DirectiveSpecificationType
    }
    interface ResponseDirectiveSpecification : DirectiveSpecification {
        override val type: DirectiveSpecificationType
            get() = DirectiveSpecificationType.RESPONSE

        fun createHandler(directive: String): ResponseDirectiveHandler
    }
    interface RequestDirectiveSpecification : DirectiveSpecification {
        override val type: DirectiveSpecificationType
            get() = DirectiveSpecificationType.REQUEST

        fun createHandler(directive: String): RequestDirectiveHandler
    }
    data class DirectiveHandlers(
        val responseHandler: ResponseDirectiveHandler?,
        val requestHandler: RequestDirectiveHandler?
    )

    fun parseDirectives(directives: List<String>): DirectiveHandlers {
        val parsedDirectives = directives
            .map(::findHandler)
            .groupBy { it.first.type }
        val responseDirectives = parsedDirectives[DirectiveSpecificationType.RESPONSE]
            ?.map {
                (it.first as ResponseDirectiveSpecification).createHandler(it.second)
            }
        val requestDirectives = parsedDirectives[DirectiveSpecificationType.REQUEST]
            ?.map {
                (it.first as RequestDirectiveSpecification).createHandler(it.second)
            }

        return DirectiveHandlers(
            responseHandler = responseDirectives.combineResponseDirectives(),
            requestHandler = requestDirectives.combineRequestDirectives(),
        )
    }
    private fun findHandler(directive: String): Pair<DirectiveSpecification, String> {
        for (handler in handlers) {
            if (handler.canHandle(directive)) {
                return Pair(handler, directive)
            }
        }
        throw IllegalStateException("No directive specification found for: $directive")
    }

    private fun List<ResponseDirectiveHandler>?.combineResponseDirectives(): ResponseDirectiveHandler? {
        if (this == null) return null
        val handlers = this

        return {
            for (handler in handlers) {
                handler(this)
            }
        }
    }

    private fun List<RequestDirectiveHandler>?.combineRequestDirectives(): RequestDirectiveHandler? {
        if (this == null) return null
        val handlers = this

        return { originalRequest ->
            for (handler in handlers) {
                handler(this, originalRequest)
            }
        }
    }
}
