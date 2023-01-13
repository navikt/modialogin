package no.nav.modialogin.persistence.jdbc

import kotlinx.coroutines.*
import no.nav.modialogin.Logging.log
import no.nav.modialogin.persistence.PersistencePubSub
import org.postgresql.jdbc.PgConnection
import org.postgresql.util.PSQLException
import java.sql.SQLException
import javax.sql.DataSource

class PostgresPersistencePubSub(
    channelName: String,
    private val dataSource: DataSource,
) : PersistencePubSub(channelName, "postgres") {
    override suspend fun subscribe(retryInterval: Long) {
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

    override suspend fun publishData(data: String) {
        // Pass. This is handled internally by postgres.
    }
}
