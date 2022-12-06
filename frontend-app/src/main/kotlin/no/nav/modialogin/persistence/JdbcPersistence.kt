package no.nav.modialogin.persistence

import kotlinx.serialization.KSerializer
import no.nav.modialogin.utils.Encoding.decode
import no.nav.modialogin.utils.Encoding.encode
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource
import kotlin.time.Duration

class JdbcPersistence<KEY, VALUE>(
    scope: String,
    private val dataSource: DataSource,
    private val keySerializer: KSerializer<KEY>,
    private val valueSerializer: KSerializer<VALUE>,
) : Persistence<KEY, VALUE>(scope) {
    override suspend fun doGet(key: KEY): VALUE? {
        val value = dataSource.connection.use { connection ->
            doGet(connection, key)
        } ?: return null

        return decode(valueSerializer, value)
    }

    override suspend fun doPut(key: KEY, value: VALUE, ttl: Duration) {
        dataSource.connection.use {
            doPut(it, key, value, ttl)
        }
    }

    override suspend fun doRemove(key: KEY) {
        dataSource.connection.use {
            doRemove(it, key)
        }
    }

    override suspend fun doClean() {
        dataSource.connection.use {
            doClean(it)
        }
    }

    override suspend fun doDump(): Map<KEY, VALUE> {
        doClean()
        return dataSource.connection
            .use { connection -> doDump(connection) }
            .mapKeys { decode(keySerializer, it.key) }
            .mapValues { decode(valueSerializer, it.value) }
    }

    private fun doDump(connection: Connection): Map<String, String> {
        val stmt = connection.prepareStatement("SELECT * FROM persistence WHERE scope = ?")
        stmt.setString(1, scope)

        return stmt.executeQuery().use { rs ->
            val validResults = mutableMapOf<String, String>()
            val now = Instant.now()
            while (rs.next()) {
                val expiry = rs.getTimestamp("expiry").toInstant()
                if (expiry.isAfter(now)) {
                    validResults[rs.getString("key")] = rs.getString("value")
                }
            }
            validResults
        }
    }

    private fun doGet(connection: Connection, key: KEY): String? {
        val stmt = connection.prepareStatement("SELECT * FROM persistence WHERE scope = ? AND key = ?")
        stmt.setString(1, scope)
        stmt.setString(2, encode(keySerializer, key))
        return stmt.executeQuery().use { rs ->
            if (rs.next()) {
                val expiry = rs.getTimestamp("expiry").toInstant()
                if (expiry.isBefore(Instant.now())) {
                    doRemove(connection, key)
                    null
                } else {
                    rs.getString("value")
                }
            } else {
                null
            }
        }
    }

    private fun doPut(connection: Connection, key: KEY, value: VALUE, ttl: Duration) {
        val stmt = connection.prepareStatement(
            """
            INSERT INTO persistence(scope, key, value, expiry)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (scope, key) DO
            UPDATE SET value = ?, expiry = ?
        """.trimIndent()
        )

        val valueString = encode(valueSerializer, value)
        val expiry = Timestamp.from(Instant.now().plusSeconds(ttl.inWholeSeconds))

        stmt.setString(1, scope)
        stmt.setString(2, encode(keySerializer, key))
        stmt.setString(3, valueString)
        stmt.setTimestamp(4, expiry)
        stmt.setString(5, valueString)
        stmt.setTimestamp(6, expiry)
        stmt.execute()
    }

    private fun doRemove(connection: Connection, key: KEY) {
        val stmt = connection.prepareStatement("DELETE FROM persistence WHERE SCOPE = ? AND key = ?")
        stmt.setString(1, scope)
        stmt.setString(2, encode(keySerializer, key))
        stmt.execute()
    }

    private fun doClean(connection: Connection) {
        val stmt = connection.prepareStatement("DELETE FROM persistence where expiry < ?")
        stmt.setTimestamp(1, Timestamp.from(Instant.now()))
        stmt.execute()
    }
}