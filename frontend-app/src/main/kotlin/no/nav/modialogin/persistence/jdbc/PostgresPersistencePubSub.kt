package no.nav.modialogin.persistence.jdbc

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import no.nav.modialogin.Logging.log
import no.nav.modialogin.persistence.PersistencePubSub
import org.postgresql.jdbc.PgConnection
import org.postgresql.util.PSQLException
import java.sql.SQLException
import javax.sql.DataSource

class PostgresPersistencePubSub(
    channelName: String,
    private val dataSource: DataSource,
) : PersistencePubSub(channelName) {
    private var job: Job? = null
    private var channel = Channel<String>()
    private var running = false

    private suspend fun subscribe(retryInterval: Long = 5000) {
        while (running) {
            try {
                val listener = dataSource.connection.unwrap(PgConnection::class.java)
                val stmt = listener.prepareStatement("LISTEN  persistence_updates")
                stmt.execute()
                while (true) {
                    val notifications = listener!!.getNotifications(10 * 1000) ?: continue
                    for (notification in notifications) {
                        runBlocking(Dispatchers.IO) {
                            channel.send(notification.parameter)
                        }
                    }
                }
            } catch (e: PSQLException) {
                delay(retryInterval)
            } catch (e: SQLException) {
                log.warn(
                    """
                The Postgres Pub/Sub was unable to unwrap the PG connection from the datasource connection.
                This means that you most likely are not using a Postgres driver, and pub/sub will not be supported.
                    """.trimIndent()
                )
                throw e
            } catch (e: Exception) {
                log.error("Error when subscribing to Postgres pub/sub", e)
                break
            }
        }
    }

    override fun startSubscribing(): Flow<String> {
        doStart()
        return channel.consumeAsFlow()
    }

    override fun stopSubscribing() {
        channel.close()
        doStop()
        channel = Channel()
    }

    override suspend fun publishData(data: String) {
        // Pass. This is handled internally by postgres.
    }

    private fun doStart() {
        running = true
        log.info("Starting jdbc subscriber on channel '$channelName'")
        job = GlobalScope.launch {
            subscribe()
        }
    }

    private fun doStop() {
        log.info("Stopping the jdbc subsriber on channel '$channelName")
        running = false
        job?.cancel()
    }
}
