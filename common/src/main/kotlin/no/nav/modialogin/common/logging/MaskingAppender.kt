package no.nav.modialogin.common.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.AppenderBase

class MaskingAppender : AppenderBase<ILoggingEvent>() {
    private lateinit var appender: Appender<ILoggingEvent>

    override fun append(event: ILoggingEvent) {
        appender.doAppend(
            MaskedLoggingEvent(event)
        )
    }

    fun setAppender(appender: Appender<ILoggingEvent>) {
        this.appender = appender
    }
}