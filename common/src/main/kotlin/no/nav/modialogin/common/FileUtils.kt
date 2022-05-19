package no.nav.modialogin.common

import io.ktor.http.*
import no.nav.modialogin.common.KtorServer.log
import java.io.File

object FileUtils {
    private val processableContentTypes = arrayOf(
        ContentType.Text.Any,
        ContentType.Application.JavaScript,
        ContentType.Application.Json,
        ContentType.Application.Xml,
    )

    fun copyAndProcessFiles(src: File, dest: File, processor: (content: String) -> String) {
        src.copyRecursively(target = dest, overwrite = true)
        for (file in dest.walkTopDown()) {
            if (file.isFile) {
                val fileContentType = ContentType.defaultForFile(file)
                if (processableContentTypes.any { fileContentType.match(it) }) {
                    file.writeText(
                        processor(
                            file.readText()
                        )
                    )
                } else {
                    log.warn("Found file ($file) that could not be processed. ContentType: $fileContentType")
                }
            }
        }
    }
}
