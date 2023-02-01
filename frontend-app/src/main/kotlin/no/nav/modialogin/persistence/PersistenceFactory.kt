package no.nav.modialogin.persistence

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import no.nav.modialogin.DataSourceConfiguration
import no.nav.modialogin.FrontendAppConfig
import no.nav.modialogin.Logging.log
import no.nav.modialogin.persistence.jdbc.JdbcPersistence
import no.nav.modialogin.persistence.jdbc.PostgresPersistencePubSub
import no.nav.modialogin.persistence.redis.RedisPersistence
import no.nav.modialogin.persistence.redis.RedisPersistencePubSub
import no.nav.modialogin.utils.AuthJedisPool
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
        val persistence = if (config.redisConfig != null) {
            val redisPool = AuthJedisPool(config.redisConfig)
            val pubSub = if (config.enablePersistencePubSub) RedisPersistencePubSub("RedisPubSub", config.redisConfig) else null
            RedisPersistence(scope, keySerializer, valueSerializer, redisPool, pubSub)
        } else {
            val dbConfig = DataSourceConfiguration(config)
            DataSourceConfiguration.migrate(config, dbConfig.adminDataSource)
            val pubSub = if (config.enablePersistencePubSub) PostgresPersistencePubSub("persistence_updates", dbConfig) else null
            JdbcPersistence(scope, keySerializer, valueSerializer, dbConfig.userDataSource, pubSub)
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
