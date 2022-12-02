package no.nav.modialogin.features

import io.getunleash.Unleash
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.modialogin.common.*
import no.nav.modialogin.features.csp.CSPFeature
import no.nav.modialogin.features.templatingfeature.TemplatingFeature
import java.io.File


private const val pathParameterName = "static-content-path-parameter"
fun Application.staticFilesFromCDN(
    contextpath: String,
    cdnUrl: String,
    unleash: Unleash? = null
) {
    val templateSources = listOfNotNull(
        Templating.EnvSource,
        CSPFeature.NonceSource,
        if (unleash != null) UnleashTemplateSource.create(unleash) else null
    ).toTypedArray()
    val templateEngine = TemplatingEngine(*templateSources)

    if (tmpDir.exists()) {
        tmpDir.deleteRecursively()
        tmpDir.mkdirs()
    }

    install(IgnoreTrailingSlash)
    install(TemplatingFeature.Plugin) {
        templatingEngine = templateEngine
    }

    routing {
        route(contextpath) {
            authenticate {
                install(TemplatingFeature.EnableRouteTransform)
                get("{$pathParameterName...}") {
                    val relativePath = call.parameters.getAll(pathParameterName)?.joinToString(File.separator) ?: return@get
                    call.respondWithFile(cdnUrl, relativePath)
                }
            }
        }
    }
}

private val tmpDir = File("/tmp/cdn")
private val cdnClient = HttpClient(Apache) {
    useProxy()
    logging()
    defaultRequest {
        header(HttpHeaders.CacheControl, "no-cache")
    }
}
private val statusMap: MutableMap<String, HttpStatusCode> = mutableMapOf()

private suspend fun ApplicationCall.respondWithFile(cdnUrl: String, path: String) {
    val errorStatus = statusMap[path]
    if (errorStatus != null) {
        respond(errorStatus, errorStatus.description)
        return
    }

    val file = tmpDir.resolve(path)
    if (file.extension.isBlank()) {
        // TODO how to ensure that the index.html is "fresh"
        // MAYBE use redis to cache files from previous deploys instead? CDN has a 15 minute delay while invaliding its cache
        respondWithFile(cdnUrl, "index.html")
    } else if (file.isFile) {
        respond(LocalFileContent(file, ContentType.defaultForFile(file)))
    } else if (!file.exists()) {
        withContext(Dispatchers.IO) {
            try {
                KtorServer.log.info("Fetching resource from $cdnUrl/$path")
                val response = cdnClient.get("$cdnUrl/$path")
                if (!response.status.isSuccess()) {
                    statusMap[path] = response.status
                    respond(response.status, response.status.description)
                } else {
                    val content = response.body<ByteArray>()

                    file.parentFile.mkdirs()
                    file.createNewFile()
                    file.writeBytes(content)

                    respond(LocalFileContent(file, ContentType.defaultForFile(file)))
                }
            } catch (e: Throwable) {
                KtorServer.log.error("Error while fetching $path from CDN", e)
                respond(status = HttpStatusCode.InternalServerError, e.message ?: "Unknown error")
            }
        }

    }
}