package no.nav.modialogin.features.templatingfeature

import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.modialogin.common.FileUtils
import no.nav.modialogin.common.FileUtils.processableContentTypes
import no.nav.modialogin.common.TemplatingEngine

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

                WriterContent(
                    contentType = requireNotNull(data.contentType) { "ContentType null" },
                    body = {
                        withContext(Dispatchers.IO) {
                            val content = templateEngine.replaceVariableReferences(call, data.readFrom().readFully())
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

    private fun ApplicationCall.isRequestForFile(): Boolean {
        val uri = this.request.uri
        return FileUtils.fileRegex.containsMatchIn(uri)
    }
}
