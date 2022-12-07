package no.nav.modialogin

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil
import org.flywaydb.core.Flyway
import java.sql.Connection
import javax.sql.DataSource

class DataSourceConfiguration(val env: FrontendAppConfig) {
    val userDataSource: DataSource by lazy { createDatasource("user") }
    val adminDataSource: DataSource by lazy { createDatasource("admin") }

    private fun createDatasource(user: String): DataSource {
        val dbConfig = checkNotNull(env.database) {
            "Must have database configration to create a DataSource"
        }
        val mountPath = dbConfig.vaultMountpath
        val config = HikariConfig()
        config.jdbcUrl = dbConfig.jdbcUrl
        config.minimumIdle = 1
        config.maximumPoolSize = 6
        config.connectionTimeout = 1000
        config.maxLifetime = 20_000

        Logging.log.info("Creating DataSource to ${dbConfig.jdbcUrl}")

        if (env.appMode.locally) {
            config.username = "username"
            config.password = "password"
            return HikariDataSource(config)
        }

        return HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration(
            config,
            mountPath,
            "${env.appName}-$user"
        )
    }

    companion object {
        fun migrate(config: FrontendAppConfig, dataSource: DataSource) {
            Flyway
                .configure()
                .dataSource(dataSource)
                .also {
                    if (dataSource is HikariDataSource && !config.appMode.locally) {
                        it.initSql("SET ROLE '${config.appName}-admin'")
                    }
                }
                .load()
                .migrate()
        }

        suspend fun <T> DataSource.useConnection(block: (Connection) -> T): T? {
            return withContext(Dispatchers.IO) {
                runCatching { this@useConnection.connection.use(block) }
                    .onFailure { Logging.log.error("Database-error", it) }
                    .getOrNull()
            }
        }
    }
}