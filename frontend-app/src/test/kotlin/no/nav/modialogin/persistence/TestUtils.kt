package no.nav.modialogin.persistence

import kotlinx.serialization.KSerializer
import no.nav.modialogin.AppMode
import no.nav.modialogin.DataSourceConfiguration
import no.nav.modialogin.DatabaseConfig
import no.nav.modialogin.FrontendAppConfig
import no.nav.modialogin.persistence.jdbc.JdbcPersistence
import no.nav.modialogin.persistence.jdbc.PostgresPersistencePubSub
import no.nav.modialogin.persistence.redis.RedisPersistence
import no.nav.modialogin.persistence.redis.RedisPersistentPubSub
import no.nav.modialogin.utils.AuthJedisPool
import no.nav.modialogin.utils.RedisConfig
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import redis.clients.jedis.HostAndPort

object TestUtils {
    class RedisContainer : GenericContainer<RedisContainer>("redis:6-alpine") {
        init {
            withCommand("redis-server --requirepass password")
            withExposedPorts(6379)
        }
    }

    interface WithRedis {

        companion object {
            val container = RedisContainer()

            @BeforeAll
            @JvmStatic
            fun startContainer() {
                container.start()
            }

            @AfterAll
            @JvmStatic
            fun stopContainer() {
                container.stop()
            }
        }
    }

    private fun redisHostAndPort() = HostAndPort(WithRedis.container.host, WithRedis.container.getMappedPort(6379))

    fun <KEY, VALUE>setupSendAndReceiveRedis(scope: String, keySerializer: KSerializer<KEY>, valueSerializer: KSerializer<VALUE>, enablePubSub: Boolean? = false): Pair<RedisPersistence<KEY, VALUE>, RedisPersistence<KEY, VALUE>> {
        val hostAndPort = redisHostAndPort()
        val redisConfig = RedisConfig(hostAndPort.host, hostAndPort.port, "password")

        val sendPool = AuthJedisPool(redisConfig)
        val receivePool = AuthJedisPool(redisConfig)

        val sendPubSub = if (enablePubSub == true) RedisPersistentPubSub("test", sendPool) else null
        val receivePubSub = if (enablePubSub == true) RedisPersistentPubSub("test", receivePool) else null

        val sendRedis = RedisPersistence(scope, sendPool, keySerializer, valueSerializer, sendPubSub)
        val receiveRedis = RedisPersistence(scope, receivePool, keySerializer, valueSerializer, receivePubSub)
        return Pair(sendRedis, receiveRedis)
    }

    interface WithPostgres {
        companion object {
            val container: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:12-alpine")
                .withDatabaseName("postgres")
                .withUsername("username")
                .withPassword("password")

            @BeforeAll
            @JvmStatic
            fun startContainer() {
                container.start()
            }

            @AfterAll
            @JvmStatic
            fun stopContainer() {
                container.stop()
            }
        }
    }

    private fun postgresHostAndPort() = HostAndPort(WithPostgres.container.host, WithPostgres.container.getMappedPort(5432))

    fun <KEY, VALUE>setupSendAndReceivePostgres(scope: String, keySerializer: KSerializer<KEY>, valueSerializer: KSerializer<VALUE>, enablePubSub: Boolean? = false): Pair<JdbcPersistence<KEY, VALUE>, JdbcPersistence<KEY, VALUE>> {
        val hostAndPort = postgresHostAndPort()
        val frontendAppConfig = FrontendAppConfig(
            appName = "testApp",
            appVersion = "1.0.0",
            appMode = AppMode.LOCALLY_WITHIN_DOCKER,
            database = DatabaseConfig("jdbc:postgresql://${hostAndPort.host}:${hostAndPort.port}/postgres")
        )

        val dataSourceConfiguration = DataSourceConfiguration(frontendAppConfig)
        DataSourceConfiguration.migrate(frontendAppConfig, dataSourceConfiguration.adminDataSource)

        val pubSub = if (enablePubSub == true) PostgresPersistencePubSub("test", dataSourceConfiguration.userDataSource) else null

        val sendPostgres = JdbcPersistence(scope, dataSourceConfiguration.userDataSource, keySerializer, valueSerializer, pubSub)
        val receivePostgres = JdbcPersistence(scope, dataSourceConfiguration.userDataSource, keySerializer, valueSerializer, pubSub)
        return Pair(sendPostgres, receivePostgres)
    }
}
