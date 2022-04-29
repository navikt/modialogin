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
        routing {
            route(config.appName) {
                config.proxyConfig.forEach { proxyConfig ->
                    authenticate {
                        route("${proxyConfig.prefix}/{...}") {
                            createProxyHandler(config.appName, proxyConfig)
                        }
                    }
                }
            }
        }
    }

    private fun Route.createProxyHandler(appName: String, config: ProxyConfig) {
        val (responseHandler, requestHandler) = BFFProxy.parseDirectives(config.rewriteDirectives)
        val client = HttpClient(CIO)
        handle {
            if (responseHandler != null) {
                responseHandler(this)
            } else {
                val request = call.request
                val proxyRequestPath = request.uri.removePrefix("/$appName/${config.prefix}/")
                val proxyRequestURI = "${config.url}/${proxyRequestPath}"
                val proxyRequestHeaders = request.headers

                val proxyResponse = withContext(Dispatchers.IO) {
                    client.request(proxyRequestURI) {
                        headers {
                            proxyRequestHeaders.forEach { name, value ->
                                appendAll(name, value)
                            }
                        }

                        requestHandler?.invoke(this, request)
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