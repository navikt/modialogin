package no.nav.modialogin.common.logging

import ch.qos.logback.classic.spi.IThrowableProxy

class MaskedThrowableProxy private constructor(private val proxy: IThrowableProxy) : IThrowableProxy by proxy {
    override fun getMessage(): String = proxy.message.maskSensitiveInfo()
    override fun getCause(): IThrowableProxy? = mask(proxy.cause)
    override fun getSuppressed(): Array<IThrowableProxy?> {
        val suppressed = proxy.suppressed
        val masked = arrayOfNulls<IThrowableProxy>(suppressed.size)
        for (i in 0..suppressed.size) {
            masked[i] = mask(suppressed[i])
        }
        return masked
    }

    companion object {
        fun mask(proxy: IThrowableProxy?): IThrowableProxy? = proxy?.let(::MaskedThrowableProxy)
    }
}