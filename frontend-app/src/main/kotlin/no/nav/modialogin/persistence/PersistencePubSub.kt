package no.nav.modialogin.persistence

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import no.nav.modialogin.Logging.log
import kotlin.concurrent.thread

abstract class PersistencePubSub(
    open val channelName: String,
    private val implementationName: String
) {
    private var job: Thread? = null
    protected var channel = Channel<String>()
    protected var running = false
    fun startSubscribing(retryInterval: Long = 5000): Flow<String> {
        doStart(retryInterval)
        return channel.consumeAsFlow()
    }

    fun stopSubscribing() {
        channel.close()
        doStop()
        channel = Channel()
    }
    private fun doStart(retryInterval: Long) {
        running = true
        log.info("Starting $implementationName subscriber on channel '$channelName'")
        job = thread(isDaemon = true, priority = 1) {
            subscribe(retryInterval)
        }
    }

    private fun doStop() {
        log.info("Stopping the $implementationName subscriber on channel '$channelName")
        running = false
        job?.interrupt()
    }

    abstract fun subscribe(retryInterval: Long)
    open suspend fun publishData(data: String): Result<*> {
        return Result.success(null)
    }
}
