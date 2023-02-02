package no.nav.modialogin.persistence

import kotlinx.serialization.KSerializer
import no.nav.modialogin.*
import no.nav.modialogin.persistence.jdbc.JdbcPersistence
import no.nav.modialogin.persistence.jdbc.PostgresPersistencePubSub
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

object PostgresTestUtils {
    open class WithPostgres {
        var container: RestartablePostgresContainer? = null

        @BeforeAll
        fun setUp() {
            container = RestartablePostgresContainer()
        }

        @AfterAll
        fun tearDown() {
            container?.unsafeStop()
        }
    }

    fun <KEY, VALUE>getIntegrationTestUtils(container: RestartablePostgresContainer?, scope: String, keySerializer: KSerializer<KEY>, valueSerializer: KSerializer<VALUE>, enablePubSub: Boolean = false): PostgresIntegrationTestUtils<KEY, VALUE> {
        requireNotNull(container)

        setupAzureAdEnv()

        val frontendAppConfig = FrontendAppConfig(
            appName = "testApp",
            appVersion = "1.0.0",
            appMode = AppMode.LOCALLY_WITHIN_DOCKER,
            database = DatabaseConfig("jdbc:postgresql://${container.hostAndPort.host}:${container.hostAndPort.port}/postgres"),
        )

        val dataSourceConfiguration = DataSourceConfiguration(frontendAppConfig)

        container.afterStartupStrategy = {
            DataSourceConfiguration.migrate(frontendAppConfig, dataSourceConfiguration.adminDataSource)
        }

        val pubSub = if (enablePubSub) PostgresPersistencePubSub(PubSubConfig("persistence_updates", 1000L, 10), dataSourceConfiguration) else null

        val sendPostgres = JdbcPersistence(scope, keySerializer, valueSerializer, dataSourceConfiguration.userDataSource, pubSub)
        val receivePostgres = JdbcPersistence(scope, keySerializer, valueSerializer, dataSourceConfiguration.userDataSource, pubSub)
        return PostgresIntegrationTestUtils(frontendAppConfig, dataSourceConfiguration, sendPostgres, receivePostgres)
    }

    data class PostgresIntegrationTestUtils<KEY, VALUE>(
        val frontendAppConfig: FrontendAppConfig,
        val dataSourceConfiguration: DataSourceConfiguration,
        val sendPostgres: JdbcPersistence<KEY, VALUE>,
        val receivePostgres: JdbcPersistence<KEY, VALUE>,
    )
}
