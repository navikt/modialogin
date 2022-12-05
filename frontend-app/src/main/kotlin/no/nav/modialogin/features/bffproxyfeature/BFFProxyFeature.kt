package no.nav.modialogin.features.bffproxyfeature

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.modialogin.Logging.log
import no.nav.modialogin.ProxyConfig
import no.nav.modialogin.utils.KotlinUtils
import no.nav.modialogin.utils.Templating
import java.net.URL

class BFFProxyFeatureConfig(
    var appName: String = "",
    var proxyConfig: List<ProxyConfig> = emptyList()
)

val BFFProxyFeature = createApplicationPlugin("bff-proxy", ::BFFProxyFeatureConfig) {
    val config = pluginConfig
    with(application) {
        val bffproxy = BFFProxy(config.proxyConfig.flatMap { it.rewriteDirectives })
        routing {
            route(config.appName) {
                config.proxyConfig.forEach { proxyConfig ->
                    authenticate {
                        route("${proxyConfig.prefix}/{...}") {
                            createProxyHandler(config.appName, bffproxy, proxyConfig)
                        }
                    }
                }
            }
        }
    }
}

private fun Route.createProxyHandler(appName: String, bffProxy: BFFProxy, config: ProxyConfig) {
    val (responseHandler, requestHandler) = bffProxy.parseDirectives(config.rewriteDirectives)
    val client = HttpClient(Apache) {
        engine {
            // Setting infinte socket timeout, thus allowing source system to propagate its own timeout exception
            socketTimeout = 0
        }
        followRedirects = false
        defaultRequest {
            headers {
                append(HttpHeaders.XCorrelationId, KotlinUtils.callId())
            }
        }
    }

    handle {
        if (responseHandler != null) {
            responseHandler(this)
        } else {
            val request = call.request
            val proxyRequestPath = request.uri.removePrefix("/$appName/${config.prefix}/")
            val proxyRequestURI = Templating.replaceVariableReferences("${config.url}/$proxyRequestPath", call)

            withContext(Dispatchers.IO) {
                call.respondWith(
                    client.proxyRequest(proxyRequestURI, call, requestHandler)
                )
            }
        }
    }
}

private suspend inline fun HttpClient.proxyRequest(
    url: String,
    call: ApplicationCall,
    noinline requestHandler: RequestDirectiveHandler?
): HttpResponse {
    log.info("Proxying request to $url")
    val request = call.request
    val proxyRequestHeaders = request.headers

    val bodyBytes = request.headers[HttpHeaders.ContentLength]
        ?.toInt()
        ?.let { size ->
            val channel: ByteReadChannel = request.receiveChannel()
            val array = ByteArray(size)
            channel.readFully(array)
            array
        }

    return this.request(URL(url)) {
        method = request.httpMethod
        headers {
            proxyRequestHeaders
                .filter { key, _ ->
                    !key.equals(HttpHeaders.ContentType, ignoreCase = true) &&
                            !key.equals(HttpHeaders.ContentLength, ignoreCase = true) &&
                            !key.equals(HttpHeaders.TransferEncoding, ignoreCase = true) &&
                            !key.equals(HttpHeaders.Upgrade, ignoreCase = true)
                }
                .forEach { key, value ->
                    appendAll(key, value)
                }
        }
        bodyBytes?.let {
            contentType(request.contentType())
            setBody(it)
        }

        requestHandler?.invoke(this, call)
    }
}

@OptIn(InternalAPI::class)
private suspend inline fun ApplicationCall.respondWith(response: HttpResponse) {
    val proxiedHeaders = response.headers
    val contentType = proxiedHeaders[HttpHeaders.ContentType]
    val contentLength = proxiedHeaders[HttpHeaders.ContentLength]

    this.respond(object : OutgoingContent.WriteChannelContent() {
        override val contentLength: Long? = contentLength?.toLong()
        override val contentType: ContentType? = contentType?.let { ContentType.parse(it) }
        override val headers: Headers = Headers.build {
            appendAll(proxiedHeaders.filter { key, _ ->
                !key.equals(HttpHeaders.ContentType, ignoreCase = true) &&
                        !key.equals(HttpHeaders.ContentLength, ignoreCase = true) &&
                        !key.equals(HttpHeaders.TransferEncoding, ignoreCase = true) &&
                        !key.equals(HttpHeaders.Upgrade, ignoreCase = true)
            })
        }
        override val status: HttpStatusCode = response.status
        override suspend fun writeTo(channel: ByteWriteChannel) {
            response.content.copyAndClose(channel)
        }
    })
}
