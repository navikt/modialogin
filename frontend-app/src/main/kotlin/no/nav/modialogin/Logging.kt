package no.nav.modialogin

import org.slf4j.LoggerFactory

object Logging {
    val log = LoggerFactory.getLogger(Logging::class.java)
    val tjenestekallLogger = LoggerFactory.getLogger("SecureLog")
}