package no.nav.modialogin.persistence.jdbc

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import no.nav.modialogin.Logging
import no.nav.modialogin.Logging.log
import no.nav.modialogin.persistence.PersistentPubSub
import no.nav.modialogin.persistence.SubMessage
import org.postgresql.jdbc.PgConnection
import java.sql.SQLException
import java.time.LocalDateTime
import javax.sql.DataSource

class PostgresPersistencePubSub(
    channelName: String,
    dataSource: DataSource,
) : PersistentPubSub(channelName) {
    private var job: Job? = null
    private var channel = Channel<SubMessage>()
    private var running = false

    private var listener: PgConnection? = null
        get() {
            return field ?: throw IllegalAccessException("Can not subscribe. Make sure a PG Connection is unwrappable from the datasource.")
        }

    init {
        tryToSetUpPgConnection(dataSource)
    }

    private fun tryToSetUpPgConnection(dataSource: DataSource) {
        try {
            listener = dataSource.connection.unwrap(PgConnection::class.java)
        } catch (_: SQLException) {
            log.warn(
                "The Postgres Pub/Sub was unable to unwrap the PG connection from the datasource connection. " +
                    "\nThis means that you most likely are not using a Postgres driver, and pub/sub will not be supported."
            )
        }
    }

    private fun subscribe() {
        while (running) {
            try {
                val stmt = listener!!.prepareStatement("LISTEN  persistence_updates")
                stmt.execute()
                while (true) {
                    val notifications = listener!!.getNotifications(10 * 1000) ?: continue
                    for (notification in notifications) {
                        val (scope, encodedKey, encodedValue, expiry) = Json.decodeFromString<JdbcEncodedMessage>(notification.parameter)
                        runBlocking(Dispatchers.IO) {
                            channel.send(SubMessage("$scope:$encodedKey", encodedValue, expiry))
                        }
                    }
                }
            } catch (e: Exception) {
                log.error("Error when subscribing to Postgres pub/sub", e)
            }
        }
    }

    override fun startSubscribing(): Flow<SubMessage> {
        doStart()
        return channel.consumeAsFlow()
    }

    override fun stopSubscribing() {
        channel.close()
        doStop()
        channel = Channel()
    }

    override suspend fun publishMessage(key: String, value: String, expiry: LocalDateTime) {
        // Skip. Postgres publishes the notifications with pg_notify and a trigger
    }

    private fun doStart() {
        requireNotNull(listener)
        running = true
        Logging.log.info("Starting jdbc subscriber on channel '$channelName'")
        job = GlobalScope.launch {
            subscribe()
        }
    }

    private fun doStop() {
        Logging.log.info("Stopping the jdbc subsriber on channel '$channelName")
        running = false
        job?.cancel()
    }
}
