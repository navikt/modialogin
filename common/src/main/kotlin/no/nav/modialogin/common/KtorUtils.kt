package no.nav.modialogin.common

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8

object KtorUtils {
    fun encode(value: String): String = URLEncoder.encode(value, UTF_8)
    fun decode(value: String): String = URLDecoder.decode(value, UTF_8)
}
