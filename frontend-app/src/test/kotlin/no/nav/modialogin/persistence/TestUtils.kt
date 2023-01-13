package no.nav.modialogin.persistence

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import kotlinx.serialization.KSerializer
import no.nav.modialogin.*
import no.nav.modialogin.persistence.jdbc.JdbcPersistence
import no.nav.modialogin.persistence.jdbc.PostgresPersistencePubSub
import no.nav.modialogin.persistence.redis.RedisPersistence
import no.nav.modialogin.persistence.redis.RedisPersistencePubSub
import no.nav.modialogin.utils.AuthJedisPool
import no.nav.modialogin.utils.RedisConfig
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.testcontainers.containers.FixedHostPortGenericContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import redis.clients.jedis.HostAndPort

object TestUtils {
    class FixedRedisContainer : FixedHostPortGenericContainer<FixedRedisContainer>("redis:6-alpine") {
        init {
            withCommand("redis-server --requirepass password")
            withFixedExposedPort(6379, 6379)
        }
    }

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

    interface WithRedisFixed {

        companion object {
            val container = FixedRedisContainer()

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

    private fun redisHostAndPort(container: GenericContainer<*>) = HostAndPort(container.host, container.getMappedPort(6379))

    fun <KEY, VALUE>setupSendAndReceiveRedis(scope: String, keySerializer: KSerializer<KEY>, valueSerializer: KSerializer<VALUE>, container: GenericContainer<*> = WithRedis.container, enablePubSub: Boolean = false): Pair<RedisPersistence<KEY, VALUE>, RedisPersistence<KEY, VALUE>> {
        val hostAndPort = redisHostAndPort(container)
        val redisConfig = RedisConfig(hostAndPort.host, hostAndPort.port, "password")

        val sendPool = AuthJedisPool(redisConfig)
        val receivePool = AuthJedisPool(redisConfig)

        val sendPubSub = if (enablePubSub) RedisPersistencePubSub("test", sendPool) else null
        val receivePubSub = if (enablePubSub) RedisPersistencePubSub("test", receivePool) else null

        val sendRedis = RedisPersistence(scope, keySerializer, valueSerializer, sendPool, sendPubSub)
        val receiveRedis = RedisPersistence(scope, keySerializer, valueSerializer, receivePool, receivePubSub)
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

    interface WithPostgresFixed {
        companion object {
            val container: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:12-alpine")
                .withDatabaseName("postgres")
                .withUsername("username")
                .withPassword("password")
                .withExposedPorts(5432)
                .withCreateContainerCmdModifier {
                    it.withHostConfig(
                        HostConfig().withPortBindings(PortBinding(Ports.Binding.bindPort(5432), ExposedPort(5432)))
                    )
                }

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

    fun postgresHostAndPort(container: GenericContainer<*>) = HostAndPort(container.host, container.getMappedPort(5432))

    fun runMigration(hostAndPort: HostAndPort): DataSourceConfiguration {
        val frontendAppConfig = FrontendAppConfig(
            appName = "testApp",
            appVersion = "1.0.0",
            appMode = AppMode.LOCALLY_WITHIN_DOCKER,
            database = DatabaseConfig("jdbc:postgresql://${hostAndPort.host}:${hostAndPort.port}/postgres"),
        )

        val dataSourceConfiguration = DataSourceConfiguration(frontendAppConfig)
        DataSourceConfiguration.migrate(frontendAppConfig, dataSourceConfiguration.adminDataSource)
        return dataSourceConfiguration
    }

    fun <KEY, VALUE>setupSendAndReceivePostgres(scope: String, keySerializer: KSerializer<KEY>, valueSerializer: KSerializer<VALUE>, container: GenericContainer<*> = WithPostgres.container, enablePubSub: Boolean = false): Pair<JdbcPersistence<KEY, VALUE>, JdbcPersistence<KEY, VALUE>> {
        val hostAndPort = postgresHostAndPort(container)
        setupAzureAdEnv()

        val dataSourceConfiguration = runMigration(hostAndPort)

        val pubSub = if (enablePubSub) PostgresPersistencePubSub("test", dataSourceConfiguration.userDataSource) else null

        val sendPostgres = JdbcPersistence(scope, keySerializer, valueSerializer, dataSourceConfiguration.userDataSource, pubSub)
        val receivePostgres = JdbcPersistence(scope, keySerializer, valueSerializer, dataSourceConfiguration.userDataSource, pubSub)
        return Pair(sendPostgres, receivePostgres)
    }
}
