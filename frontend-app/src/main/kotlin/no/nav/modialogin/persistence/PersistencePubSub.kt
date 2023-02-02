package no.nav.modialogin.persistence

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import no.nav.modialogin.Logging.log
import no.nav.modialogin.PubSubConfig
import kotlin.concurrent.thread

abstract class PersistencePubSub(
    protected val pubSubConfig: PubSubConfig,
    private val implementationName: String
) {
    private var job: Thread? = null
    protected var channel = Channel<String>()
    protected var running = false
    fun startSubscribing(): Flow<String> {
        doStart()
        return channel.consumeAsFlow()
    }

    fun stopSubscribing() {
        channel.close()
        doStop()
        channel = Channel()
    }
    private fun doStart() {
        running = true
        log.info("Starting $implementationName subscriber on channel '${pubSubConfig.channelName}'")
        job = thread(isDaemon = true, priority = 5) {
            subscribe()
        }
    }

    private fun doStop() {
        log.info("Stopping the $implementationName subscriber on channel '${pubSubConfig.channelName}'")
        running = false
        job?.interrupt()
    }

    abstract fun subscribe()
    open suspend fun publishData(data: String): Result<*> {
        return Result.success(null)
    }
}
