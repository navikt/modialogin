package no.nav.modialogin.persistence

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import no.nav.modialogin.DataSourceConfiguration
import no.nav.modialogin.FrontendAppConfig
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.minutes

object PersistenceFactory {
    fun <KEY, VALUE> create(
        scope: String,
        config: FrontendAppConfig,
        keySerializer: KSerializer<KEY>,
        valueSerializer: KSerializer<VALUE>,
    ): Persistence<KEY, VALUE> {
        val persistence = if (config.redis != null) {
            RedisPersistence(scope, config.redis, keySerializer, valueSerializer)
        } else {
            val dbConfig = DataSourceConfiguration(config)
            DataSourceConfiguration.migrate(config, dbConfig.adminDataSource)

            JdbcPersistence(scope, dbConfig.userDataSource, keySerializer, valueSerializer)
        }

        fixedRateTimer(
            daemon = true,
            initialDelay = 5.minutes.inWholeMicroseconds,
            period = 1.minutes.inWholeMicroseconds,
        ) {
            runBlocking {
                persistence.clean()
            }
        }

        return persistence
    }

}