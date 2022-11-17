package no.nav.modialogin.features.unleashtemplatefeature

import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.modialogin.common.TemplatingEngine
import no.nav.modialogin.common.unleash.UnleashService
import no.nav.modialogin.features.UnleashTemplateExtension
import no.nav.modialogin.features.isRequestForFile

class FeatureTogglePluginConfig(
    var contextpath: String? = null,
    var unleashService: UnleashService? = null
)
val UnleashTemplateFeature = createRouteScopedPlugin("UnleashTemplate", { FeatureTogglePluginConfig() }) {
    val config = this.pluginConfig
    val unleash = requireNotNull(config.unleashService)
    val templateEngine = TemplatingEngine(UnleashTemplateExtension.Source)
    onCallRespond { call ->
        val isProbablyARequestForFile = call.isRequestForFile()
                || call.request.uri == "/${config.contextpath}"
                || call.request.uri == "/${config.contextpath}/"

        if (!isProbablyARequestForFile) return@onCallRespond

        transformBody { data ->
            if (data !is OutgoingContent.ReadChannelContent) {
                return@transformBody data
            }
            if (data.contentType?.match("text/*") == false) {
                return@transformBody data
            }

            WriterContent(
                contentType = requireNotNull(data.contentType) { "ContentType null" },
                body = {
                    withContext(Dispatchers.IO) {
                        val ctx = UnleashTemplateExtension.Context(call, unleash)
                        val content = templateEngine.replaceVariableReferences(ctx, data.readFrom().readFully())
                        write(content)
                    }
                }
            )
        }
    }
}

private suspend fun ByteReadChannel.readFully(): String = buildString {
    while (!isClosedForRead) {
        append(readUTF8Line() ?: "")
    }
}