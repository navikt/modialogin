package no.nav.modialogin.features.templatingfeature

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.modialogin.utils.TemplatingEngine

object TemplatingFeature {
    private val enabledForRoute = AttributeKey<Boolean>("templating.feature.route.transform.enabled")
    class Config(
        var templatingEngine: TemplatingEngine<ApplicationCall?>? = null,
    )

    val Plugin = createApplicationPlugin("TemplatingPluging", ::Config) {
        val config = this.pluginConfig
        val templateEngine = requireNotNull(config.templatingEngine) {
            "TemplateEngine must be specified"
        }

        onCallRespond { call ->
            if (call.attributes.getOrNull(enabledForRoute) != true) return@onCallRespond

            transformBody { data ->
                if (data !is OutgoingContent.ReadChannelContent) {
                    return@transformBody data
                }
                if (processableContentTypes.all { data.contentType?.match(it) == false }) {
                    return@transformBody data
                }

                val content = templateEngine.replaceVariableReferences(call, data.readFrom().readFully())
                WriterContent(
                    contentType = requireNotNull(data.contentType) { "ContentType null" },
                    body = {
                        withContext(Dispatchers.IO) {
                            write(content)
                        }
                    }
                )
            }
        }
    }

    val EnableRouteTransform = createRouteScopedPlugin("EnableTemplatingPluging") {
        onCall { call ->
            call.attributes.put(enabledForRoute, true)
        }
    }

    private suspend fun ByteReadChannel.readFully(): String = buildString {
        while (!isClosedForRead) {
            append(readUTF8Line() ?: "")
        }
    }

    private val processableContentTypes = arrayOf(
        ContentType.Text.Any,
        ContentType.Application.JavaScript,
        ContentType.Application.Json,
        ContentType.Application.Xml,
    )
}
