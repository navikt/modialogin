package no.nav.modialogin.common.features.bffproxyfeature

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import no.nav.modialogin.common.KtorServer.log
import no.nav.modialogin.common.Templating
import no.nav.modialogin.common.features.maskSensitiveInfo

object BFFProxyFeature {
    class Config(
        val appName: String,
        val proxyConfig: List<ProxyConfig>
    )

    @Serializable
    data class ProxyConfig(
        val prefix: String,
        val url: String? = null,
        val rewriteDirectives: List<String> = emptyList()
    )

    fun Application.installBFFProxy(config: Config) {
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

    private fun Route.createProxyHandler(appName: String, bffProxy: BFFProxy, config: ProxyConfig) {
        val (responseHandler, requestHandler) = bffProxy.parseDirectives(config.rewriteDirectives)
        val client = HttpClient(CIO)
        val targetUrl = Templating.replaceVariableReferences(config.url ?: "", null)
        handle {
            if (responseHandler != null) {
                responseHandler(this)
            } else {
                val request = call.request
                val proxyRequestPath = request.uri.removePrefix("/$appName/${config.prefix}/")
                val proxyRequestURI = Templating.replaceVariableReferences(
                    "$targetUrl/$proxyRequestPath",
                    request
                )
                val proxyRequestHeaders = request.headers

                log.info("Proxying request to ${proxyRequestURI.maskSensitiveInfo()}")
                val proxyResponse = withContext(Dispatchers.IO) {
                    client.request(proxyRequestURI) {
                        headers {
                            proxyRequestHeaders.forEach { name, value ->
                                appendAll(name, value)
                            }
                        }

                        requestHandler?.invoke(this, call)
                    }
                }

                call.respond(
                    ByteArrayContent(
                        contentType = proxyResponse.contentType(),
                        status = proxyResponse.status,
                        bytes = proxyResponse.readBytes()
                    )
                )
            }
        }
    }
}
