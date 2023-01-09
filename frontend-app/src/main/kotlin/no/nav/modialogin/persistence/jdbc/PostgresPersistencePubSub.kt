package no.nav.modialogin.persistence.jdbc

import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.pool.HikariPool
import kotlinx.coroutines.*
import no.nav.modialogin.DataSourceConfiguration
import no.nav.modialogin.Logging.log
import no.nav.modialogin.PubSubConfig
import no.nav.modialogin.persistence.PersistencePubSub
import org.postgresql.jdbc.PgConnection
import org.postgresql.util.PSQLException
import java.sql.*

class PostgresPersistencePubSub(
    pubSubConfig: PubSubConfig,
    private val dataSourceConfig: DataSourceConfiguration,
) : PersistencePubSub(pubSubConfig, "postgres") {

    private fun testConnection(listener: PgConnection) {
        val stmt = listener.createStatement()
        val rs = stmt.executeQuery("SELECT 1")
        rs.close()
        stmt.close()
    }

    override fun subscribe() {
        while (running) {
            var dataSource: HikariDataSource? = null
            var listener: PgConnection? = null

            try {
                dataSource = dataSourceConfig.createDatasource("user", 0, 1)
                listener = dataSource.connection.unwrap(PgConnection::class.java)

                val stmt = listener.prepareStatement("LISTEN ${pubSubConfig.channelName}")
                stmt.execute()
                stmt.close()

                while (true) {
                    testConnection(listener)
                    val notifications = listener!!.notifications

                    if (notifications != null) {
                        runBlocking(Dispatchers.IO) {
                            for (notification in notifications) {
                                channel.send(notification.parameter)
                            }
                        }
                    }
                    Thread.sleep(pubSubConfig.subRetryInterval)
                }
            } catch (e: Exception) {
                when (e) {
                    is PSQLException, is SQLTransientConnectionException, is HikariPool.PoolInitializationException -> {
                        log.warn("Error when subscribing to Postgres pub/sub", e)
                        if (listener != null) dataSource?.evictConnection(listener)
                        dataSource?.close()
                        Thread.sleep(pubSubConfig.subRetryInterval)
                    }
                    is SQLException -> {
                        log.warn(
                            """
                The Postgres Pub/Sub was unable to unwrap the PG connection from the datasource connection.
                This means that you most likely are not using a Postgres driver, and pub/sub will not be supported.
                            """.trimIndent()
                        )
                        dataSource?.close()
                        break
                    }
                    else -> {
                        log.error("Fatal error when subscribing to Postgres pub/sub. Won't try to reconnect.", e)
                        dataSource?.close()
                        break
                    }
                }
            }
        }
    }
}
