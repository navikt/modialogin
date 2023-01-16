package no.nav.modialogin.persistence

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import no.nav.modialogin.Logging.log

abstract class PersistencePubSub(
    open val channelName: String,
    private val implementationName: String
) {
    private var job: Job? = null
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
        log.info("Starting $implementationName subscriber on channel '$channelName'")
        job = GlobalScope.launch {
            subscribe()
        }
    }

    private fun doStop() {
        log.info("Stopping the $implementationName subscriber on channel '$channelName")
        running = false
        job?.cancel()
    }

    abstract suspend fun subscribe(retryInterval: Long = 5000)
    abstract suspend fun publishData(data: String)
}
