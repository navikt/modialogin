package no.nav.modialogin.common

import io.ktor.http.*

object FileUtils {
    val processableContentTypes = arrayOf(
        ContentType.Text.Any,
        ContentType.Application.JavaScript,
        ContentType.Application.Json,
        ContentType.Application.Xml,
    )
    val fileRegex = Regex("\\.(?:css|js|jpe?g|gif|ico|png|xml|otf|ttf|eot|woff|svg|map|json)$")
}
