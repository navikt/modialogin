package no.nav.modialogin.persistence

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import no.nav.modialogin.DataSourceConfiguration
import no.nav.modialogin.FrontendAppConfig
import no.nav.modialogin.Logging.log
import kotlin.concurrent.fixedRateTimer
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.minutes

object PersistenceFactory {
    private val cleanupInitialDelay = 5.minutes
    private val cleanupPeriod = 1.minutes

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

        log.info("Starting cleanup timer [delay:${cleanupInitialDelay.inWholeSeconds}s, period:${cleanupPeriod.inWholeSeconds}s]")
        fixedRateTimer(
            daemon = true,
            initialDelay = cleanupInitialDelay.inWholeMilliseconds,
            period = cleanupPeriod.inWholeMilliseconds,
        ) {
            val time = measureTimeMillis {
                runBlocking {
                    persistence.clean()
                }
            }
            log.info("Persistence cleanup ran in $time ms")
        }

        return persistence
    }
}