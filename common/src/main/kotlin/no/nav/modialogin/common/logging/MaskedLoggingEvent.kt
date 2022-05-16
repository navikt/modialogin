package no.nav.modialogin.common.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy

class MaskedLoggingEvent(private val event: ILoggingEvent) : ILoggingEvent by event {
    override fun getFormattedMessage(): String? = event.formattedMessage?.maskSensitiveInfo()
    override fun getThrowableProxy(): IThrowableProxy? = MaskedThrowableProxy.mask(event.throwableProxy)
    override fun getMDCPropertyMap(): MutableMap<String?, String?>? = maskedMap(event.mdcPropertyMap)
    override fun getMdc(): MutableMap<String?, String?>? = maskedMap(event.mdc)

    private fun maskedMap(map: Map<String?, String?>?): MutableMap<String?, String?>? {
        return map
            ?.mapValues { entry -> entry.value?.maskSensitiveInfo() }
            ?.toMutableMap()
    }
}